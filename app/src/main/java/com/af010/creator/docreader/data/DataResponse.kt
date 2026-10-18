package com.d010.pdf.reader.viewmodel

sealed class DataResponse<T>(val loadingStatus: LoadingStatus) {
    class DataIdle<T> : DataResponse<T>(LoadingStatus.Idle)

    class DataLoading<T>(val loadingType: LoadingStatus = LoadingStatus.Loading)
        : DataResponse<T>(loadingType) {
        init {
            require(loadingType == LoadingStatus.Loading || loadingType == LoadingStatus.LoadingMore || loadingType == LoadingStatus.Refresh) {}
        }
    }

    class DataError<T>(val message: String) : DataResponse<T>(LoadingStatus.Error)

    data class DataSuccess<T>(val data: T,val ts: Long = System.currentTimeMillis()) : DataResponse<T>(LoadingStatus.Success)

}

enum class LoadingStatus {
    Idle,       // Chưa tải dữ liệu
    Loading,    // Đang tải dữ liệu lần đầu
    LoadingMore, // Đang tải thêm
    Refresh,    // Làm mới dữ liệu
    Success,    // Thành công
    Error       // Lỗi
}