package app.cinderblock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.cinderblock.data.Controller

class CinderViewModel(app: Application) : AndroidViewModel(app) {
    val controller: Controller = (app as CinderblockApp).controller
}
