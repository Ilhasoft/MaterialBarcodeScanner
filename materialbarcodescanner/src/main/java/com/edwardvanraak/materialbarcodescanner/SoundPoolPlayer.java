package com.edwardvanraak.materialbarcodescanner;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;

import java.util.HashMap;

class SoundPoolPlayer {

    private SoundPool shortPlayer = null;
    private HashMap sounds = new HashMap();

    SoundPoolPlayer(Context pContext) {
        this.shortPlayer = new SoundPool(4, AudioManager.STREAM_MUSIC, 0);
        sounds.put(R.raw.bleep, this.shortPlayer.load(pContext, R.raw.bleep, 1));
    }

    void playShortResource(int piResource) {
        int iSoundId = (Integer) sounds.get(piResource);
        this.shortPlayer.play(iSoundId, 0.99f, 0.99f, 0, 0, 1);
    }

    void release() {
        this.shortPlayer.release();
    }
}
