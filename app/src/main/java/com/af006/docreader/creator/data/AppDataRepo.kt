package com.af010.creator.docreader.data

import android.content.Context
import com.af010.creator.docreader.R
import com.af010.creator.docreader.model.FolderItem
import com.af010.creator.docreader.model.RecentDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class AppDataRepo(val context: Context) {

    val listAllData : StateFlow<List<RecentDocument>>  field =  MutableStateFlow(mutableListOf<RecentDocument>())
    val listAllImage : StateFlow<List<RecentDocument>>  field =  MutableStateFlow(mutableListOf<RecentDocument>())
    fun setListAllData(list: MutableList<RecentDocument>) {
        listAllData.value = list
    }

    fun removeItemInList(path: String) {
        listAllData.value = listAllData.value.filter { it.path != path }.toMutableList()
    }

    fun groupToFolderList(list: List<RecentDocument>): MutableList<FolderItem> {
        if (list.isEmpty()) return mutableListOf()
        val groupedFolders = list
            .groupBy { File(it.path).parentFile?.name.orEmpty() }
            .filter { (_, items) -> items.isNotEmpty() }
            .map { (folderName, items) ->
                FolderItem(
                    folderName = folderName,
                    previewPath = items.first().path,
                    listData = items.toMutableList(),
                    itemCount = items.size
                )
            }
        val allFolder = FolderItem(
            folderName = context.getString(R.string.all_photo),
            previewPath = list.first().path,
            listData = list.toMutableList(),
            itemCount = list.size
        )

        return (mutableListOf(allFolder) + groupedFolders) as MutableList<FolderItem>
    }

    fun renameAndGetPath(oldFile: File, newName: String): String? {
        val parent = oldFile.parentFile ?: return null
        val newFile = File(parent, newName)
        return newFile.absolutePath
    }
}