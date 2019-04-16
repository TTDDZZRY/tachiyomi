/*
 * Copyright (C) 2018 The Tachiyomi Open Source Project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package tachiyomi.ui.category

import com.freeletics.coredux.SideEffect
import com.freeletics.coredux.SimpleSideEffect
import com.freeletics.coredux.createStore
import com.jakewharton.rxrelay2.BehaviorRelay
import io.reactivex.Observable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tachiyomi.domain.library.interactor.CreateCategoryWithName
import tachiyomi.domain.library.interactor.DeleteCategories
import tachiyomi.domain.library.interactor.GetCategories
import tachiyomi.domain.library.interactor.RenameCategory
import tachiyomi.domain.library.interactor.ReorderCategory
import tachiyomi.domain.library.model.Category
import tachiyomi.ui.presenter.BasePresenter
import javax.inject.Inject

class CategoryPresenter @Inject constructor(
  private val getCategories: GetCategories,
  private val createCategoryWithName: CreateCategoryWithName,
  private val deleteCategories: DeleteCategories,
  private val renameCategory: RenameCategory,
  private val reorderCategory: ReorderCategory
) : BasePresenter() {

  /**
   * Behavior subject containing the last emitted view state.
   */
  private val state = BehaviorRelay.create<ViewState>()

  /**
   * State subject as a consumer-only observable.
   */
  val stateObserver: Observable<ViewState> = state

  private val store = scope.createStore(
    name = "Category presenter",
    initialState = getInitialViewState(),
    sideEffects = getSideEffects(),
    reducer = { state, action -> action.reduce(state) }
  )

  init {
    store.subscribeToChangedStateUpdatesInMain { state.accept(it) }
    loadCategories()
  }

  private fun getInitialViewState(): ViewState {
    return ViewState()
  }

  private fun getSideEffects(): List<SideEffect<ViewState, Action>> {
    val sideEffects = mutableListOf<SideEffect<ViewState, Action>>()

    // Category creation side effect
    sideEffects += SimpleSideEffect("Create a category") { _, action, _, handler ->
      when (action) {
        is Action.CreateCategory -> handler {
          createCategoryWithName.await(action.name)
          null // Ignore results (for now)
        }
        else -> null
      }
    }

    // Category deletion side effect
    sideEffects += SimpleSideEffect("Delete categories") { _, action, _, handler ->
      when (action) {
        is Action.DeleteCategories -> handler {
          val result = deleteCategories.await(action.categoryIds)
          when (result) {
            // Unselect categories when the operation succeeds
            DeleteCategories.Result.Success -> Action.UnselectCategories
            else -> null
          }
        }
        else -> null
      }
    }

    // Rename categories side effect
    sideEffects += SimpleSideEffect("Rename a category") { _, action, _, handler ->
      when (action) {
        is Action.RenameCategory -> handler {
          val result = renameCategory.await(action.categoryId, action.newName)
          when (result) {
            // Unselect categories when the operation succeeds
            RenameCategory.Result.Success -> Action.UnselectCategories
            else -> null
          }
        }
        else -> null
      }
    }

    // Reorder categories side effect
    sideEffects += SimpleSideEffect("Reorder a category") { _, action, _, handler ->
      when (action) {
        is Action.ReorderCategory -> handler {
          reorderCategory.await(action.category, action.newPosition)
          null // Do nothing for now
        }
        else -> null
      }
    }

    return sideEffects
  }

  private fun loadCategories() {
    scope.launch(dispatchers.computation) {
      getCategories.subscribe().collect {
        store.dispatch(Action.CategoriesUpdate(it))
      }
    }
  }

  fun createCategory(name: String) {
    store.dispatch(Action.CreateCategory(name))
  }

  fun deleteCategories(categories: Set<Long>) {
    store.dispatch(Action.DeleteCategories(categories))
  }

  fun renameCategory(categoryId: Long, newName: String) {
    store.dispatch(Action.RenameCategory(categoryId, newName))
  }

  fun reorderCategory(category: Category, newPosition: Int) {
    store.dispatch(Action.ReorderCategory(category, newPosition))
  }

  fun toggleCategorySelection(category: Category) {
    store.dispatch(Action.ToggleCategorySelection(category))
  }

  fun unselectCategories() {
    store.dispatch(Action.UnselectCategories)
  }

  fun getCategory(id: Long): Category? {
    return state.value?.categories?.find { it.id == id }
  }

}
