package com.example.data.network

import com.example.data.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ServerUIApi {

    @GET("healthz")
    suspend fun checkHealth(): Response<ResponseBody>

    @GET("api/servers")
    suspend fun listServers(): ServerListResponse

    @POST("api/servers")
    suspend fun createServer(@Body input: ServerInput): ServerPublic

    @GET("api/servers/{id}")
    suspend fun getServer(@Path("id") id: String): ServerMetrics

    @PUT("api/servers/{id}")
    suspend fun updateServer(@Path("id") id: String, @Body input: ServerInput): ServerPublic

    @DELETE("api/servers/{id}")
    suspend fun deleteServer(@Path("id") id: String): SimpleStatus

    @POST("api/servers/test-connection")
    suspend fun testServerDraft(@Body request: ServerDraftRequest): ServerDraftTest

    @POST("api/servers/{id}/test-connection")
    suspend fun testServer(@Path("id") id: String): ServerDraftTest

    @POST("api/servers/{id}/connect")
    suspend fun connectServer(@Path("id") id: String): ServerMetrics

    @POST("api/servers/{id}/disconnect")
    suspend fun disconnectServer(@Path("id") id: String): ServerPublic

    @GET("api/server/metrics")
    suspend fun getLiveMetrics(@Query("serverId") serverId: String): ServerMetrics

    @GET("api/server/process")
    suspend fun inspectProcess(
        @Query("serverId") serverId: String,
        @Query("pid") pid: Int
    ): ProcessDetail

    @POST("api/server/process/kill")
    suspend fun killProcess(@Body body: KillProcessRequest): SimpleStatus

    @POST("api/server/service")
    suspend fun controlService(@Body body: ControlServiceRequest): SimpleStatus

    @GET("api/files")
    suspend fun listFiles(
        @Query("serverId") serverId: String,
        @Query("path") path: String = "~"
    ): FileListResponse

    @GET("api/files/read")
    suspend fun readFile(
        @Query("serverId") serverId: String,
        @Query("path") path: String
    ): FilePreview

    @POST("api/files/write")
    suspend fun writeFile(@Body body: WriteFileRequest): SimpleStatus

    @POST("api/files/create")
    suspend fun createFile(@Body body: CreateFileRequest): SimpleStatus

    @POST("api/files/mkdir")
    suspend fun mkdir(@Body body: MkdirRequest): SimpleStatus

    @POST("api/files/rename")
    suspend fun rename(@Body body: RenameRequest): SimpleStatus

    @DELETE("api/files")
    suspend fun deleteFile(
        @Query("serverId") serverId: String,
        @Query("path") path: String
    ): SimpleStatus

    @POST("api/files/compress")
    suspend fun compress(@Body body: CompressRequest): SimpleStatus

    @POST("api/files/extract")
    suspend fun extract(@Body body: ExtractRequest): SimpleStatus
}
