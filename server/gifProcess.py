import io
import os
import html
import shutil
import datetime
import subprocess
import moviepy.editor as mpe
import concurrent.futures as futures

from GifItem import *
from GifZipItem import *
from goldberg import *
from elasticsearch import Elasticsearch
from elasticsearch_driver import SignatureES
from GcloudVisionApi import GcloudVisionApi
from google.cloud import translate_v2 as translate
from awsDB import *

es = Elasticsearch(['localhost:9234'])
ses = SignatureES(es)
GVAPI = GcloudVisionApi()
TAPI = translate.Client.from_service_account_json("gapiKey.json")

#process funcs
def createGifItem(gifname):
    gifItem = GifItem(gifname)
    gifItem.calKeyFrames()
    return gifItem

def createGifZipItem(dirname):
    gifZipItem = GifZipItem(dirname)
    gifZipItem.calKeyFrames()
    return gifZipItem

def toChinese(text):
    text = html.unescape(
        TAPI.translate(
        text, target_language='zh')['translatedText'])
    return text

#need to create a "images" index first in elasticsaerch
#curl -X PUT "http://localhost:9200/images"
def searchKeyFrames(keyframes):
    ex = futures.ThreadPoolExecutor(5)
    tasks = []
    candidates = []

    for frame in keyframes:
        tasks.append(ex.submit(ses.search_image, frame))
    for task in futures.as_completed(tasks):
        res = task.result()
        for itm in res:
            if itm['dist'] <= 0.4:
                candidates.append(itm)
    
    if len(candidates) > 0:
        cnts = {}
        for i,cand in enumerate(candidates):
            if cand['metadata'] != None:
                dbkey = cand['metadata']['dbkey']
            else:
                continue
            if dbkey not in cnts:
                cnts[dbkey] = []
            cnts[dbkey].append(i)
        #get the most frequent id
        cnts = list(sorted(cnts.items(), key=lambda item: len(item[1]), reverse=True))
        if cnts[0][1] == 1:
            return sorted(candidates, key=lambda k: k['dist'])[0]
        else:
            return candidates[cnts[0][1][0]]
    return None

def getImageLabel(img):
    label = GVAPI.understandImage(img)
    res = 'Automatically generated ' 
    if 'caption' in label:
        res += 'caption is '+label['caption'] + '. '
    res += 'Best guess is '+label['best_guess'] +\
            '. The labels are ' + ' and '.join(label['labels'])
    if 'text' in label:
        res += '. The text in the image is '+label['text']+'.'
    return res

def getDynamoAnnotation(gifID):
    itm = get_dynamo_item(gifID)
    if itm != None:
        return itm['annotation']

def insertItemToElastic(gifID, viditem):
    for i in range(len(viditem.keyframes)):
        ses.add_image(gifID+str(i), viditem.keyframes[i], metadata={'dbkey':gifID, 'isGif':True})
    
#bg tasks

#convert gif to mp4 and upload it
def gifToVideoNUpload(gifID, gifname, mp4fname, label):
    try:
        #we make width/height to even number
        command = 'ffmpeg -i '+gifname+' -movflags faststart -pix_fmt yuv420p '+\
        '-vf "scale=trunc(iw/2)*2:trunc(ih/2)*2" '+mp4fname
        subprocess.call(command,shell=True)
        upload_file_wpath(mp4fname, gifID+'.mp4')
        create_dynamo_entry(gifID, gifID+".mp4", "true", "true", label)
        #clear files after upload
        os.remove(gifname)
        os.remove(mp4fname)
    except OSError as e:
        print(e)

#convert a set of imgs to mp4 and upload it
# durations: in secs
def imgsToVideoNUpload(gifID, gitem, mp4fname, label, dirtoremove):
    #to mp4
    gitem.saveToMp4(mp4fname)
    upload_file_wpath(mp4fname, gifID+'.mp4')
    print('[step]saved mp4')
    #save to db
    insertItemToElastic(gifID, gitem)

    create_dynamo_entry(gifID, gifID+".mp4", "false", "true", label)

    print(gifID, "upload finished")
    #clear the folder after upload
    try:
        shutil.rmtree(dirtoremove)
    except OSError as e:
        print(e)

#not gif, only upload the image
def imgUpload(gifID, keyframe, pngpath, label, dirtoremove):
    #save to db
    upload_file_wpath(pngpath, gifID+'.jpg')
    ses.add_image(gifID, keyframe, metadata={'dbkey':gifID, 'isGif':False})
    create_dynamo_entry(gifID, gifID+".jpg", "false", "false", label)
    
    print('here add an image')
    #clear the folder after upload
    try:
        shutil.rmtree(dirtoremove)
    except OSError as e:
        print(e)

