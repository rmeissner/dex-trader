package de.thegerman.dextrader.ui.main

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import de.thegerman.dextrader.R
import kotlinx.android.synthetic.main.screen_main.*
import org.koin.android.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModelContract by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_main)

        screen_main_connect_btn.isVisible = false
        screen_main_connect_btn.setOnClickListener {
            viewModel.performAction(MainViewModelContract.Action.StartSession)
        }
        screen_main_disconnect_btn.isVisible = false
        screen_main_disconnect_btn.setOnClickListener {
            viewModel.performAction(MainViewModelContract.Action.DisconnectSession)
        }
        Log.d("#####", "Test")
        viewModel.state.observe(this, Observer  {
            screen_main_lbl.text = if(it.loading) "Loading" else it.connectedAccount ?: "Waiting"
            screen_main_connect_btn.isVisible = !it.sessionActive
            screen_main_disconnect_btn.isVisible = it.sessionActive

            it.viewAction?.let { update -> performAction(update) }
        })
    }

    private fun performAction(viewAction: MainViewModelContract.ViewAction) {
        when(viewAction) {
            is MainViewModelContract.ViewAction.OpenUri -> {
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse(viewAction.uri)
                startActivity(i)
            }
        }
    }
}
