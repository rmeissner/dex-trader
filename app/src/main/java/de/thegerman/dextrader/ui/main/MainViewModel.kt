package de.thegerman.dextrader.ui.main

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import de.thegerman.dextrader.repositories.AssetRepository
import de.thegerman.dextrader.repositories.SessionRepository
import de.thegerman.dextrader.ui.base.BaseViewModel
import de.thegerman.dextrader.utils.asMiddleEllipsized
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress

abstract class MainViewModelContract : BaseViewModel() {
    abstract val state: LiveData<State>

    abstract fun performAction(action: Action)

    data class State(
        val loading: Boolean,
        val sessionActive: Boolean,
        val connectedAccount: Account?,
        val assets: List<Asset>,
        var viewAction: ViewAction?
    )

    data class Account(val address: Solidity.Address, val displayAddress: String, val displayName: String?)

    data class Asset(val contract: String, val token: String, val image: String?)

    sealed class Action {
        object LoadSession : Action()
        object StartSession : Action()
        object DisconnectSession : Action()
    }

    sealed class ViewAction {
        data class OpenUri(val uri: String) : ViewAction()
    }
}

class MainViewModel(
    private val assetRepository: AssetRepository,
    private val sessionRepository: SessionRepository
) : MainViewModelContract() {
    private val inChannel = Channel<Action>(UNLIMITED)
    private val stateChannel = Channel<State>()

    override val state = liveData {
        inChannel.send(Action.LoadSession)
        process()
        for (state in stateChannel) emit(state)
    }

    // Initial state
    private var currentState = State(loading = false, sessionActive = false, connectedAccount = null, assets = emptyList(), viewAction = null)

    private suspend fun updateState(state: State) {
        // Clear already submitted viewAction
        if (currentState.viewAction == state.viewAction) state.viewAction = null
        Log.d("#####", "updateState $state")
        checkReloadAssets(state.connectedAccount)
        currentState = state
        stateChannel.send(state)
    }

    override fun performAction(action: Action) {
        viewModelScope.launch {
            inChannel.send(action)
        }
    }

    private fun process() {
        viewModelScope.launch {
            for (action in inChannel) {
                Log.d("#####", "nextAction $action")
                when (action) {
                    Action.LoadSession -> loadActiveSession()
                    Action.StartSession -> startSession()
                    Action.DisconnectSession -> disconnectSession()
                }
            }
        }
    }

    private fun accountFromSession(session: SessionRepository.SessionData): Account? =
        session.approvedAccounts?.firstOrNull()?.let {
            val address = it.asEthereumAddress() ?: return@let null
            Account(address, address.asEthereumAddressChecksumString().asMiddleEllipsized(4), session.peerName)
        }

    private fun State.inactive() = copy(sessionActive = false, connectedAccount = null, assets = emptyList())

    /*
     * Watch Session
     */

    private var watcherJob: Job? = null

    private suspend fun watchSession() {
        watcherJob?.cancel()
        watcherJob = null
        val channel = withContext(viewModelScope.coroutineContext) { sessionRepository.sessionUpdatesChannel() }
        watcherJob = viewModelScope.launch {
            for (session in channel) {
                updateState(session.approvedAccounts?.let {
                    currentState.copy(sessionActive = true, connectedAccount = accountFromSession(session))
                } ?: currentState.inactive())
            }
        }.also {
            it.invokeOnCompletion { channel.cancel() }
        }
    }

    /*
     * Load Assets
     */

    private var assetLoading: Job? = null

    private fun checkReloadAssets(account: Account?) {
        if (currentState.connectedAccount?.address == account?.address) return // Noting to load
        account?.address?.let { loadAssets(it) }
    }

    private fun loadAssets(owner: Solidity.Address) {
        // TODO: show loading
        assetLoading?.cancel()
        assetLoading = viewModelScope.launch {
            try {
                val assets = assetRepository.loadAssets(owner)
                updateState(currentState.copy(assets = assets.map {
                    Asset(
                        it.contractName ?: it.contract.asEthereumAddressChecksumString().asMiddleEllipsized(4),
                        it.name ?: it.id.toString().asMiddleEllipsized(4),
                        it.image
                    )
                }))
            } catch (e: Exception) {
                Log.d("#####", "error $e")
            } finally {
                assetLoading = null
            }
        }
    }

    /*
     * Load Session Info
     */

    private suspend fun loadActiveSession() {
        if (currentState.loading) return // Already loading
        updateState(currentState.copy(loading = true))
        val activeSession = sessionRepository.activeSessionAsync()
        viewModelScope.launch {
            try {
                activeSession.await()?.let { session ->
                    updateState(currentState.copy(sessionActive = true, connectedAccount = accountFromSession(session)))
                    watchSession()
                } ?: run {
                    updateState(currentState.inactive())
                }
            } catch (e: Exception) {
                updateState(currentState.inactive())
                Log.d("#####", "error $e")
            } finally {
                updateState(currentState.copy(loading = false))
            }
        }
    }

    /*
     * Start Session
     */

    private suspend fun startSession() {
        if (currentState.loading || currentState.sessionActive) return // Already loading or active
        updateState(currentState.copy(loading = true))
        val createSession = sessionRepository.createSessionAsync()
        viewModelScope.launch {
            try {
                val uri = createSession.await()
                watchSession()
                updateState(currentState.copy(sessionActive = true, viewAction = ViewAction.OpenUri(uri)))
            } catch (e: Exception) {
                Log.d("#####", "error $e")
            } finally {
                updateState(currentState.copy(loading = false))
            }
        }
    }

    /*
     * Disconnect Session
     */

    private suspend fun disconnectSession() {
        val disconnectSession = sessionRepository.disconnectSessionAsync()
        viewModelScope.launch {
            try {
                if (disconnectSession.await()) {
                    updateState(currentState.inactive())
                }
            } catch (e: Exception) {
                Log.d("#####", "error $e")
            }
        }
    }
}