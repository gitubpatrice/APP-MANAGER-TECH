package com.filestech.appmanager.domain.model

/**
 * Ordering applied to the app catalogue.
 *
 * Lives in `domain/model/` (Phase VIII C5 fix) so use cases and ViewModels
 * never import from the `data/` package just to reference an enum that has
 * no DataStore dependency by itself.
 *
 * **Persistence**: stored as enum name (TEXT) in DataStore by
 * [com.filestech.appmanager.data.local.datastore.SettingsRepositoryImpl].
 * Never rename values without shipping a DataStore migration — unknown
 * strings collapse silently to the default (NAME_ASC).
 */
enum class AppSortOrder {
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    LAST_USED_DESC,
    INSTALL_DATE_DESC,
}
