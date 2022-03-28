import os
import sys
import shutil
import base64
import logging
import datetime
import tornado.web
import tornado.ioloop
import tornado.options
import tornado.websocket
import tornado.autoreload
import time 
from zipfile import ZipFile
from datetime import datetime
from concurrent.futures import ThreadPoolExecutor
from gifProcess import *


def _init_asyncio_patch():
    """
    Select compatible event loop for Tornado 5+.
    As of Python 3.8, the default event loop on Windows is `proactor`,
    however Tornado requires the old default "selector" event loop.
    As Tornado has decided to leave this to users to set, MkDocs needs
    to set it. See https://github.com/tornadoweb/tornado/issues/2608.
    """
    if sys.platform.startswith("win") and sys.version_info >= (3, 8):
        import asyncio
        try:
            from asyncio import WindowsSelectorEventLoopPolicy
        except ImportError:
            pass  # Can't assign a policy which doesn't exist.
        else:
            if not isinstance(asyncio.get_event_loop_policy(), WindowsSelectorEventLoopPolicy):
                asyncio.set_event_loop_policy(WindowsSelectorEventLoopPolicy())

def getCurTimeStamp():
    return datetime.datetime.now().strftime('%Y%m%d_%H%M%S_%f')

# use threading for long latency ops
executor = ThreadPoolExecutor(5)
currentGif = 0
gifUrls = []
with open('gifurls') as f:
    lines = f.readlines()
    for line in lines:
        gifUrls.append(line.strip())

# handle anontation request and the request from the website
class UploadHandler(tornado.web.RequestHandler):
    def set_default_headers(self):
        # print ("setting headers!!!")
        self.set_header("Access-Control-Allow-Origin", "*")
        self.set_header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept")
        self.set_header('Access-Control-Allow-Methods', 'POST, GET, OPTIONS')
        self.set_header('Content-Type', 'application/json; charset=UTF-8')

    def options(self):
        # no body
        self.set_status(204)
        self.finish()

    async def get(self):
        self.set_status(201)
        self.finish()

    async def post(self):
        remote_ip = self.request.headers.get("X-Real-IP") or \
                    self.request.headers.get("X-Forwarded-For") or \
                    self.request.remote_ip
        print('post from ip:', remote_ip)
        try:
            # handle add gif annotation from the annotation website
            data = tornado.escape.json_decode(self.request.body)
            if 'filetype' in data:
                #'filetype' need to be 'gif'
                if data['filetype'] == 'gif':
                    if 'url' in data:
                        print('url: '+ data['url'])
                    elif 'file' in data:
                        #strip the 'data:image/gif;base64,' part
                        imgdata = base64.b64decode(data['file'].split("base64,")[1])
                        # create a unique id for the GIF
                        gifID = getCurTimeStamp()
                        gifname = 'tmpfiles/'+gifID+'.gif'
                        mp4name = 'tmpfiles/'+gifID+'.mp4'
                        with open(gifname, 'wb') as f:
                            f.write(imgdata)

                        #create key frames, search if exist, and create new entry
                        gitem = createGifItem(gifname)
                        searchRes = searchKeyFrames(gitem.keyframes)

                        if searchRes == None:
                            insertItemToElastic(gifID, gitem)
                            
                            # create new entry in bg
                            executor.submit(gifToVideoNUpload, gifID, 
                                gifname, mp4name, data['annotation'])
                            self.set_status(201)
                            self.write({'status': 'succeed'})
                        else:
                            self.set_status(201)
                            self.write({'status': 'exist'})
        except:
            # handle gif annotation request from the android client
            # the gif file type is an zip of the screenshort sequences
            # together with a metadata json file
            # curl -F "filetype=imgs" -F "zipfile=@testfile.zip" http://127.0.0.1:8080/upload
            uuid = self.get_body_argument('uuid', 'null')
            loglang = self.get_body_argument('log', 'null')
            print('uuid : ' + uuid)
            if loglang in ['chinese', 'english']:
                with open("log.txt", 'a', encoding="utf-8") as logf:
                    logf.write("[%s] uuid:%s ip:%s oldgif language:%s\n" % (getCurTimeStamp(), uuid, remote_ip, loglang))
            elif 'imgs' == self.get_body_argument('filetype', 'null'):
                #a image request with zip
                if 'zipfile' in self.request.files:
                    zipID = getCurTimeStamp()
                    zipname = 'tmpfiles/'+zipID+'.zip'
                    with open(zipname, 'wb') as f:
                        f.write(self.request.files['zipfile'][0]['body'])
                    #extract the zip file
                    zipfolder = 'tmpfiles/tmpzip'+zipID
                    with ZipFile(zipname, 'r') as zip_ref:
                        zip_ref.extractall(zipfolder)
                    os.remove(zipname)

                    gitem = createGifZipItem(zipfolder+'/')
                    #search if exist
                    searchRes = searchKeyFrames(gitem.keyframes)
                    
                    if searchRes == None:
                        print("Getting Annotation and insert new item")
                        #get label and then return first
                        #the timeconsuming task in a separate thread (img->video, upload)
                        annotation = getImageLabel(gitem.keyframes[0])
                        if len(gitem.keyframes) == 1:
                            gitem.saveKeyFrame(zipfolder+'/tmp.png')
                            executor.submit(imgUpload, zipID, gitem.keyframes[0], zipfolder+'/tmp.png',
                                    annotation, zipfolder)
                        else: 
                            executor.submit(imgsToVideoNUpload, zipID, gitem,
                                zipfolder+'/tmp.mp4', annotation, zipfolder)
                        if gitem.language == 'cn':
                            annotation = toChinese(annotation)
                        with open("log.txt", 'a', encoding="utf-8") as logf:
                            logf.write("[%s] uuid:%s ip:%s newgif language:%s\n" % (zipID, uuid, remote_ip, gitem.language))
                        self.set_status(201)
                        self.write({'label': annotation})
                    else:
                        annotation = getDynamoAnnotation(searchRes['metadata']['dbkey'])
                        with open("log.txt", 'a', encoding="utf-8") as logf:
                            logf.write("[%s] uuid:%s ip:%s oldgif language:%s\n" % (zipID, uuid, remote_ip, gitem.language))
                        #clear the folder after upload
                        try:
                            shutil.rmtree(zipfolder)
                        except OSError as e:
                            print(e)
                        self.set_status(201)
                        if gitem.language == 'cn':
                            annotation = toChinese(annotation)
                        self.write({'label': annotation})

            self.set_status(201)

class MyApplication(tornado.web.Application):
    is_closing = False

    def signal_handler(self, signum, frame):
        logging.info('exiting...')
        self.is_closing = True

    def try_exit(self):
        if self.is_closing:
            # clean up here
            tornado.ioloop.IOLoop.instance().stop()
            logging.info('exit success')

def make_app():
    return MyApplication([
        (r"/upload", UploadHandler),
    ], debug=True)

if __name__ == "__main__":
    _init_asyncio_patch()
    app = make_app()
    http_server = tornado.httpserver.HTTPServer(app)
    http_server.listen(8080)
    print ("listen...")

    tornado.autoreload.start()
    for dir, _, files in os.walk('.'):
        for f in files:
            if (not f.startswith('.')) and (not f.endswith("txt")):
                tornado.autoreload.watch(dir + '/' + f) 

    tornado.ioloop.PeriodicCallback(app.try_exit, 200).start()
    tornado.ioloop.IOLoop.current().start()

# python -W ignore server.py
