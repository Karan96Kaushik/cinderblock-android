package com.bayonetbarontech.cinderblock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bayonetbarontech.cinderblock.data.Controller

class CinderViewModel(app: Application) : AndroidViewModel(app) {
    val controller: Controller = (app as CinderblockApp).controller
}
