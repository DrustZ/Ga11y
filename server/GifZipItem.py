import pylab
import imageio
import numpy as np
import os
import math
import json
import time
import random
from ImageUtils import *
import moviepy.video.VideoClip as VC
from moviepy.editor import concatenate_videoclips

#a gif class for handling zip request from the mobile side
class GifZipItem:
    def __init__(self, folderpath):
        self.folderpath = folderpath
        self.dup_frames = []
        #-1 is not calinterval yet, 0 is no loop, in ms
        self.loop_interval = -1 
        self.keyframes = []
        self.language = 'en'

        if not os.path.isfile(folderpath+'metadata.json'):
            print('not a valid folder!')
            return

        with open(folderpath+'metadata.json') as f:
            self.metadata = json.load(f)

        self.imgs =[]
        self.imginfo = [] #[idx, time, duration]

        print("frames cnt: ", len(self.metadata['images']))
        if len(self.metadata['images']) > 0:
            for i, item in enumerate(self.metadata['images']):
                tstamp = list(item.keys())[0]
                imgfname = item[tstamp]
                self.imgs.append(Image.open(folderpath+imgfname))
                duration = int(tstamp)
                if i > 0:
                    duration -= self.imginfo[-1][1]
                self.imginfo.append([i, int(tstamp), duration])
        
        self.imginfo = np.array(self.imginfo)

        if self.metadata['chinese'] == True:
            self.language = 'cn'

    #cal culate the inner loop interval of the current gif folder
    def calLoopInterval(self):
        seen_frames = {}
        duplicate_frames = {}
        last_frame = np.array(self.imgs[0])
        all_diff = np.zeros(last_frame.shape)

        for x in range(len(self.imgs)):
            # get frame x
            frame = np.array(self.imgs[x])

            diff = (frame != last_frame)
            all_diff += diff
            last_frame = frame

            # hash our frame
            hashed = ahash(frame,32)
            
            if seen_frames.get(hashed, None):
                # if we've seen this frame before, add it to the list of frames 
                # that all have the same hashed value in duplicate_frames
                # also we remove the frames that too close to this frame
                if x-duplicate_frames[hashed][-1] == 1:
                    duplicate_frames[hashed].pop()
                duplicate_frames[hashed].append(x)
            else:
                # if it's the first time seeing a frame, put it in seen_frames
                seen_frames[hashed] = x
                duplicate_frames[hashed] = [x]

        # if self.metadata['findContour'] != True:
        self.findContourFromBoxes(all_diff)

        dupframes = np.array(
            [duplicate_frames[x] for x in duplicate_frames 
            if len(duplicate_frames[x]) > 1 ])

        if len(dupframes) == 0:
            # no duplicate in video
            self.loop_interval = 0
            self.finalseq = self.imginfo[:, :2]
            self.durations = self.imginfo[:, -1]
            return
        else:
            #get mode of the intervals
            dup_frame_cnt = np.array([len(x) for x in dupframes])
            most_freq_cnt = np.bincount(dup_frame_cnt).argmax()
            itemindex = np.where(dup_frame_cnt==most_freq_cnt)

            dup_frames = np.array(list(dupframes[itemindex]))
            intervals = self.imginfo[dup_frames[:, 1]][:, 1]\
                 - self.imginfo[dup_frames[:, 0]][:, 1]
            #get max interval
            self.loop_interval = intervals.max()
            self.interval_flags = dup_frames[intervals.argmax()]
            self.interpolateGif()

    #manually find the contour from the bounding boxes
    def findContourFromBoxes(self, all_diff):
        #we ignore the border of the crop
        all_diff[0, :, :] = 0
        all_diff[-1, :, :] = 0
        all_diff[:, 0, :] = 0
        all_diff[:, -1, :] = 0
        if not np.any(all_diff):
            h0, h1, w0, w1 = 0, all_diff.shape[0], 0, all_diff.shape[1]
        else:
            hs, ws = np.nonzero(all_diff.sum(axis=2))
            h0, h1, w0, w1 = hs.min(), hs.max(), ws.min(), ws.max()

        #we need to find the contour manually by the bounding boxes
        if len(self.metadata['rects']) > 1:
            zerox, zeroy = 1e10, 1e10
            #get the base point xy
            for rect in self.metadata['rects']:
                zerox = min(zerox, rect[0])
                zeroy = min(zeroy, rect[1])
            
            minh, minw, hh, ww = 1e10, 1e10, 1e10, 1e10
            for rect in self.metadata['rects']:
                x0, y0, x1, y1 =\
                rect[0]-zerox, rect[1]-zeroy, rect[2]-zerox, rect[3]-zeroy
                x0, y0, x1, y1 = int(x0/2), int(y0/2), math.ceil(x1/2), math.ceil(y1/2)
                #if larger than the diff bound
                if x0 <= w0 and y0 <= h0 and x1 >= w1 and y1 >= h1:
                    wid = x1-x0
                    hid = y1-y0
                    if wid <= ww and hid <= hh:
                        ww = wid
                        hh = hid
                        minh = y0
                        minw = x0
            self.contour = [minh, minw, hh, ww]
        else:
            self.contour = 0, 0, all_diff.shape[0], all_diff.shape[1]

    #interpolate the current loop
    #because the recorded images might not be complete for each loop
    #due to the recording delay
    def interpolateGif(self):
        #interpolate the gif
        startidx = self.interval_flags[0]
        endidx = self.interval_flags[1]

        #make the startidx timestamp to 0
        newtstamp = self.imginfo[:][:,1] - self.imginfo[startidx][1]
        #get the preceding portion up to 1 interval
        preindex = np.where((newtstamp > -self.loop_interval) & (newtstamp < 0))[0]
        prestamps = newtstamp[preindex]+self.loop_interval
        pre_seq = list(zip(preindex, prestamps))

        #get the post portion up to 1 self.loop_interval
        postindex = np.where((newtstamp > self.loop_interval) & (newtstamp < 2*self.loop_interval))[0]
        poststamps = newtstamp[postindex]-self.loop_interval
        post_seq = list(zip(postindex, poststamps))

        #get the current portion inside the self.loop_interval
        curindex = np.where((newtstamp >= 0) & (newtstamp < self.loop_interval))[0]
        curstamps = newtstamp[curindex]
        cur_seq = list(zip(curindex, curstamps))

        interpolated = pre_seq + post_seq + cur_seq
        interpolated.sort(key=lambda x:x[1])

        #remove the frames that are too near with each other
        lastt = -100
        finalseq = [] #img_index, tstamp
        for i in range(len(interpolated)):
            if interpolated[i][1] - lastt > 20:
                finalseq.append(interpolated[i])
                lastt = interpolated[i][1]

        self.finalseq = np.array(finalseq)
        self.durations = np.append(
            self.finalseq[1:, 1] - self.finalseq[:-1, 1], 
            self.loop_interval-self.finalseq[-1, 1])

    def saveKeyFrame(self, pngpath):
        if len(self.keyframes) == 0:
            return
        Image.fromarray(self.keyframes[0]).save(pngpath)

    def saveToMp4(self, mp4fpath):
        clips = []
        h0,w0, h, w = self.contour
        #we have to make the video width/height even
        #in order to play in browser, because of h264 yuv420
        if h % 2 == 1:
            h -= 1
        if w % 2 == 1:
            w -= 1

        for i, m in enumerate(self.finalseq):
            cropped = np.array(self.imgs[ m[0] ])[h0:h0+h, w0:w0+w, :]
            clips.append(VC.ImageClip(
                cropped).set_duration(self.durations[i]/1000))

        concat_clip = concatenate_videoclips(clips, method="compose")
        concat_clip.write_videofile(mp4fpath, fps=15)

    #find visually similar images of a range of indices
    def getDistinctFrames(self, indices, scale=16):
        seen_frames = {}
        frame_idx = []
        for x in indices:
            # get frame x
            frame = np.array(self.imgs[x])

            # hash our frame
            hashed = ahash(frame, scale)
            if not seen_frames.get( hashed, None):
                # if it's the first time seeing a frame, put it in seen_frames
                seen_frames[hashed] = x
                frame_idx.append(x)
        return np.array(frame_idx)

    #cal culate the keyframes of the current video
    def calKeyFrames(self, maxn=5):
        assert maxn > 0

        if len(self.keyframes) > 0:
            return

        if self.loop_interval == -1:
            self.calLoopInterval()

        frame_cnt = len(self.imgs)
        if self.loop_interval == 0:
            indices = self.getDistinctFrames(range(frame_cnt))
            # no loop, we just get the key frame from whole vid
            self.keyframe_idces = indices[np.arange(
                0, len(indices), max(1, int(len(indices)/maxn)))[:maxn]]
        else:
            indices = self.getDistinctFrames(self.finalseq[:,0])
            inter = max(int(len(indices)/maxn), 1)
            self.keyframe_idces = indices[np.arange(
                0, len(indices), inter)[:maxn]]
        
        h0,w0, h, w = self.contour
        print('[countour]: ', self.contour)
        self.keyframes = np.array([
            np.array(self.imgs[i])[h0:h0+h, w0:w0+w, :] for i in self.keyframe_idces])
