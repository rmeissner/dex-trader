package de.thegerman.dextrader.ui.main

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import de.thegerman.dextrader.repositories.SessionRepository
import de.thegerman.dextrader.ui.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class MainViewModelContract : BaseViewModel() {
    abstract val state: LiveData<State>

    abstract fun performAction(action: Action)

    data class State(val loading: Boolean, val sessionActive: Boolean, val connectedAccount: String?, var viewAction: ViewAction?)

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
    private val sessionRepository: SessionRepository
) : MainViewModelContract() {
    private val inChannel = Channel<Action>(UNLIMITED)
    private val stateChannel = Channel<State>()

    override val state = liveData {
        inChannel.send(Action.LoadSession)
        process()
        for (state in stateChannel) emit(state)
    }

    // Intital state
    private var currentState = State(loading = false, sessionActive = false, connectedAccount = null, viewAction = null)

    private suspend fun updateState(state: State) {
        // Clear already submitted viewAction
        if (currentState.viewAction == state.viewAction) state.viewAction = null
        Log.d("#####", "updateState $state")
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

    private var watcherJob: Job? = null

    private suspend fun watchSession() {
        watcherJob?.cancel()
        watcherJob = null
        val channel = withContext(viewModelScope.coroutineContext) { sessionRepository.sessionUpdatesChannel() }
        watcherJob = viewModelScope.launch {
            for (session in channel) {
                updateState(
                    currentState.copy(
                        sessionActive = session.approvedAccounts != null,
                        connectedAccount = session.approvedAccounts?.firstOrNull()
                    )
                )
            }
        }.also {
            it.invokeOnCompletion { channel.cancel() }
        }
    }

    /*
     * Session loading logic
     */

    private suspend fun loadActiveSession() {
        if (currentState.loading) return // Already loading
        updateState(currentState.copy(loading = true))
        val activeSession = sessionRepository.activeSessionAsync()
        viewModelScope.launch {
            try {
                activeSession.await()?.let {
                    updateState(currentState.copy(sessionActive = true, connectedAccount = it.approvedAccounts?.firstOrNull()))
                    watchSession()
                } ?: run {
                    updateState(currentState.copy(sessionActive = false, connectedAccount = null))
                }
            } catch (e: Exception) {
                updateState(currentState.copy(sessionActive = false))
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
     * Start Session
     */

    private suspend fun disconnectSession() {
        val disconnectSession = sessionRepository.disconnectSessionAsync()
        viewModelScope.launch {
            try {
                if (disconnectSession.await()) {
                    updateState(currentState.copy(sessionActive = false, connectedAccount = null))
                }
            } catch (e: Exception) {
                Log.d("#####", "error $e")
            }
        }
    }
}