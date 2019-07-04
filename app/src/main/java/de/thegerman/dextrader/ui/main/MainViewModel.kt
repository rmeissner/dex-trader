package de.thegerman.dextrader.ui.main

import de.thegerman.dextrader.ui.base.BaseViewModel

abstract class MainViewModelContract: BaseViewModel() {
    abstract fun getId(): String


}

class MainViewModel(private val id: String): MainViewModelContract() {
    override fun getId() = id
}