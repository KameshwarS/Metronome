package com.example.metronome

import android.net.Uri

data class MetronomeSound(val name: String, val resourceId: Int?=null,val uri:Uri?=null, var soundPoolId: Int = 0){
    init{

        require((resourceId!=null&&uri==null)||(resourceId==null&&uri!=null)){
            //one of either uri or id

        }
    }
}