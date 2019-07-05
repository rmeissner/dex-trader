package de.thegerman.dextrader.ui.main

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import de.thegerman.dextrader.R
import de.thegerman.dextrader.repositories.AssetRepository
import kotlinx.android.synthetic.main.item_asset.view.*
import kotlinx.android.synthetic.main.screen_main.*
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString

class MainActivity : AppCompatActivity() {

    private val picasso: Picasso by inject()

    private val adapter = AssetAdapter(picasso)

    private val viewModel: MainViewModelContract by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_main)

        screen_main_account_assets_list.adapter = adapter
        screen_main_account_assets_list.layoutManager = LinearLayoutManager(this)

        screen_main_connect_btn.isVisible = false
        screen_main_connect_btn.setOnClickListener {
            viewModel.performAction(MainViewModelContract.Action.StartSession)
        }
        screen_main_disconnect_btn.isVisible = false
        screen_main_disconnect_btn.setOnClickListener {
            viewModel.performAction(MainViewModelContract.Action.DisconnectSession)
        }
        viewModel.state.observe(this, Observer {
            screen_main_account_info_group.isVisible = it.sessionActive
            it.connectedAccount?.let { account ->
                screen_main_account_address_img.setAddress(account.address)
                screen_main_account_address_lbl.text = account.displayAddress
                screen_main_account_name_lbl.text = account.displayName ?: "Unknown client"
            } ?: run {
                screen_main_account_address_img.setAddress(null)
                screen_main_account_address_lbl.text = null
                screen_main_account_name_lbl.text = "Waiting for client information"
            }
            screen_main_connect_btn.isVisible = !it.sessionActive

            it.viewAction?.let { update -> performAction(update) }

            Log.d("#####", "submitList ${it.assets}")
            adapter.submitList(it.assets)
        })
    }

    private fun performAction(viewAction: MainViewModelContract.ViewAction) {
        when (viewAction) {
            is MainViewModelContract.ViewAction.OpenUri -> {
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse(viewAction.uri)
                startActivity(i)
            }
        }
    }

    private class AssetAdapter(private val picasso: Picasso) : ListAdapter<MainViewModelContract.Asset, AssetAdapter.ViewHolder>(DIFF_CALLBACK) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_asset, parent, false), picasso)

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        override fun onViewRecycled(holder: ViewHolder) {
            super.onViewRecycled(holder)
            holder.unbind()
        }

        companion object {
            private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MainViewModelContract.Asset>() {
                override fun areItemsTheSame(old: MainViewModelContract.Asset, new: MainViewModelContract.Asset) =
                    // User properties may have changed if reloaded from the DB, but ID is fixed
                    old.contract == new.contract && old.token == new.token

                override fun areContentsTheSame(old: MainViewModelContract.Asset, new: MainViewModelContract.Asset) =
                    old == new
            }
        }

        private class ViewHolder(
            itemView: View, private val picasso: Picasso
        ) : RecyclerView.ViewHolder(itemView) {

            fun bind(asset: MainViewModelContract.Asset) {

                itemView.item_asset_contract_lbl.text = asset.contract
                itemView.item_asset_name_lbl.text = asset.token

                picasso.cancelRequest(itemView.item_asset_img)
                itemView.item_asset_img.setImageDrawable(null)
                if (!asset.image.isNullOrBlank())
                    picasso.load(asset.image).into(itemView.item_asset_img)
            }

            fun unbind() {
                picasso.cancelRequest(itemView.item_asset_img)
            }
        }
    }
}
