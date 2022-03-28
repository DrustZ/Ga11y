import pylab
import imageio
import numpy as np
import time
from ImageUtils import *

# a class that encapsulates a gif
class GifItem:
    def __init__(self, fpath):
        self.fpath = fpath
        self.vid = imageio.get_reader(fpath)
        self.frame_cnt = len(self.vid)
        self.dup_frames = []
        self.loop_interval = -1 #-1 is not calinterval yet, 0 is no loop
        self.keyframes = []

    #find visually similar images of a range of indices
    def getDistinctFrames(self, indices, scale=16):
        seen_frames = {}
        frame_idx = []
        for x in indices:
            # get frame x
            frame = self.vid.get_data(x)
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
        
        indices = self.getDistinctFrames(range(self.frame_cnt))
        # no loop, we just get the key frame from whole vid
        self.keyframe_idces = indices[np.arange(
            0, len(indices), max(1, int(len(indices)/maxn)))[:maxn]]
        self.keyframes = np.array([self.vid.get_data(i % self.frame_cnt) for i in self.keyframe_idces])
