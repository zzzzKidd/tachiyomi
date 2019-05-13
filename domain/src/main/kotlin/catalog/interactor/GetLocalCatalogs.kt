/*
 * Copyright (C) 2018 The Tachiyomi Open Source Project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package tachiyomi.domain.catalog.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combineLatest
import kotlinx.coroutines.flow.map
import tachiyomi.domain.catalog.model.CatalogLocal
import tachiyomi.domain.catalog.model.CatalogSort
import tachiyomi.domain.catalog.repository.CatalogRepository
import tachiyomi.domain.library.repository.LibraryRepository
import javax.inject.Inject

class GetLocalCatalogs @Inject constructor(
  private val catalogRepository: CatalogRepository,
  private val libraryRepository: LibraryRepository
) {

  fun subscribe(sort: CatalogSort = CatalogSort.Favorites): Flow<List<CatalogLocal>> {
    val internalFlow = catalogRepository.getInternalCatalogsFlow()
    val installedFlow = catalogRepository.getInstalledCatalogsFlow()

    val combinedFlow = internalFlow.combineLatest(installedFlow) { internal, installed ->
      internal + installed
    }

    return when (sort) {
      CatalogSort.Name -> sortByName(combinedFlow)
      CatalogSort.Favorites -> sortByFavorites(combinedFlow)
    }
  }

  private fun sortByName(catalogsFlow: Flow<List<CatalogLocal>>): Flow<List<CatalogLocal>> {
    return catalogsFlow.map { catalogs ->
      catalogs.sortedBy { it.name }
    }
  }

  private fun sortByFavorites(catalogsFlow: Flow<List<CatalogLocal>>): Flow<List<CatalogLocal>> {
    var position = 0
    val favoriteIds = libraryRepository.findFavoriteSourceIds().associateWith { position++ }

    return catalogsFlow.map { catalogs ->
      catalogs.sortedWith(FavoritesComparator(favoriteIds).thenBy { it.name })
    }
  }

  private class FavoritesComparator(val favoriteIds: Map<Long, Int>) : Comparator<CatalogLocal> {

    override fun compare(c1: CatalogLocal, c2: CatalogLocal): Int {
      val pos1 = favoriteIds[c1.source.id] ?: Int.MAX_VALUE
      val pos2 = favoriteIds[c2.source.id] ?: Int.MAX_VALUE

      return pos1.compareTo(pos2)
    }

  }

}