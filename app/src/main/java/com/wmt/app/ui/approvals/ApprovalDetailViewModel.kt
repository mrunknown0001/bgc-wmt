package com.wmt.app.ui.approvals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wmt.app.domain.model.ApprovalComment
import com.wmt.app.domain.model.ApprovalDecisionType
import com.wmt.app.domain.model.ApprovalRequestDetail
import com.wmt.app.domain.repository.ApprovalRepository
import com.wmt.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApprovalDetailUiState(
    val loading: Boolean = true,
    val detail: ApprovalRequestDetail? = null,
    val error: String? = null,
    /** A decision is in flight. The buttons stay disabled until the server answers. */
    val deciding: Boolean = false,
    val postingComment: Boolean = false,
    /**
     * Comments posted from this screen, shown straight away. The detail payload carries
     * every comment unpaginated, so a later refresh supersedes these; the getter below
     * drops the duplicates.
     */
    val postedComments: List<ApprovalComment> = emptyList(),
    /** A resubmission or cancellation is in flight. */
    val submittingAction: Boolean = false,
    /**
     * Set once the request is cancelled. The record is soft-deleted server-side, so the
     * screen leaves rather than showing something that no longer exists.
     */
    val cancelled: Boolean = false,
    /** One-shot message, normally the server own wording for what just happened. */
    val message: String? = null,
) {
    val request get() = detail?.request

    /** The comments on the record, plus anything posted here that it has not caught up to. */
    val comments: List<ApprovalComment>
        get() {
            val loaded = detail?.comments.orEmpty()
            val seen = loaded.mapTo(mutableSetOf()) { it.id }
            return loaded + postedComments.filterNot { it.id in seen }
        }
}

/**
 * Backs the request detail screen, including the decision itself.
 *
 * Deciding is not optimistic. Two approvers can hold the same request open at once, and
 * a quorum that looks unmet on one phone may already have been met on another, so the
 * button waits for the server and then shows what the server actually did.
 */
@HiltViewModel
class ApprovalDetailViewModel @Inject constructor(
    private val repository: ApprovalRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Int = checkNotNull(savedStateHandle["projectId"])
    val requestId: Int = checkNotNull(savedStateHandle["requestId"])

    private val _state = MutableStateFlow(ApprovalDetailUiState())
    val state: StateFlow<ApprovalDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /**
     * Records this user decision on the active step.
     *
     * The response carries the updated request and the sentence the server wants shown,
     * which is the wording the web app uses too, so both are taken from it rather than
     * being composed here.
     */
    fun decide(decision: ApprovalDecisionType, comment: String?) {
        if (_state.value.deciding) return
        _state.update { it.copy(deciding = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.decide(projectId, requestId, decision, comment)) {
                is Resource.Success -> {
                    _state.update {
                        it.copy(
                            deciding = false,
                            // The server returns the request in its new state, so the
                            // outcome shows without waiting for a re-fetch.
                            detail = it.detail?.copy(
                                request = result.data.request,
                                canDecide = false,
                            ),
                            message = result.data.message,
                        )
                    }
                    // Steps and decisions moved underneath, so pull the full record back
                    // for the timeline.
                    load(isRefresh = true)
                }
                // A refusal here is usually a real answer: someone else met the quorum
                // first, or the step has moved on. Show the server words and reload to
                // reflect where the request actually stands.
                is Resource.Error -> {
                    _state.update { it.copy(deciding = false, message = result.error.message) }
                    load(isRefresh = true)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    /**
     * Sends a returned request back out to its chain.
     *
     * Only offered when the payload says canResubmit: the server allows it while the
     * request sits with its author, and refuses once it is moving again.
     */
    fun resubmit() {
        if (_state.value.submittingAction) return
        _state.update { it.copy(submittingAction = true) }
        viewModelScope.launch {
            when (val result = repository.resubmit(projectId, requestId)) {
                is Resource.Success -> {
                    _state.update {
                        it.copy(
                            submittingAction = false,
                            detail = it.detail?.copy(request = result.data),
                            message = "Request resubmitted.",
                        )
                    }
                    // The chain started a fresh attempt, so the timeline has changed.
                    load(isRefresh = true)
                }
                is Resource.Error -> _state.update {
                    it.copy(submittingAction = false, message = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    /** Withdraws the request. The server soft-deletes it and cancels its open steps. */
    fun cancelRequest() {
        // Also guarded on cancelled: withdrawing is terminal, and a second call would
        // ask the server to delete a record that is already gone.
        if (_state.value.submittingAction || _state.value.cancelled) return
        _state.update { it.copy(submittingAction = true) }
        viewModelScope.launch {
            when (val result = repository.cancelRequest(projectId, requestId)) {
                is Resource.Success -> _state.update {
                    it.copy(submittingAction = false, cancelled = true)
                }
                is Resource.Error -> _state.update {
                    it.copy(submittingAction = false, message = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun addComment(body: String) {
        if (body.isBlank() || _state.value.postingComment) return
        _state.update { it.copy(postingComment = true) }
        viewModelScope.launch {
            when (val result = repository.addComment(projectId, requestId, body)) {
                is Resource.Success -> _state.update {
                    it.copy(
                        postingComment = false,
                        postedComments = it.postedComments + result.data,
                    )
                }
                is Resource.Error -> _state.update {
                    it.copy(postingComment = false, message = result.error.message)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun load(isRefresh: Boolean = false) {
        _state.update { it.copy(loading = !isRefresh && it.detail == null, error = null) }
        viewModelScope.launch {
            when (val result = repository.request(projectId, requestId)) {
                is Resource.Success -> _state.update {
                    it.copy(loading = false, detail = result.data, error = null)
                }
                // A failed refresh keeps what is on screen; a failed first load has
                // nothing to keep, so it becomes the screen.
                is Resource.Error -> _state.update {
                    if (it.detail == null) {
                        it.copy(loading = false, error = result.error.message)
                    } else {
                        it.copy(loading = false, message = result.error.message)
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }

}
