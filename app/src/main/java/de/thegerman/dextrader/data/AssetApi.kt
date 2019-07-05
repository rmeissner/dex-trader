package de.thegerman.dextrader.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

interface AssetApi {
    @GET("v1/assets")
    suspend fun assets(@Query("owner") owner: String): AssetsResponse

    companion object {
        const val BASE_URL = "https://api.opensea.io/api/"
    }

    @JsonClass(generateAdapter = true)
    data class AssetsResponse(
        @Json(name = "assets") val assets: List<Asset>
    )

    @JsonClass(generateAdapter = true)
    data class Asset(
        @Json(name = "token_id") val tokenId: String,
        @Json(name = "name") val name: String?,
        @Json(name = "description") val description: String?,
        @Json(name = "external_link") val externalLink: String?,
        @Json(name = "asset_contract") val contract: AssetContract,
        @Json(name = "image_url") val imageUrl: String?,
        @Json(name = "image_preview_url") val imagePreviewUrl: String?
    )

    @JsonClass(generateAdapter = true)
    data class AssetContract(
        @Json(name = "address") val address: String,
        @Json(name = "name") val name: String?,
        @Json(name = "description") val description: String?,
        @Json(name = "external_link") val externalLink: String?,
        @Json(name = "image_url") val imageUrl: String?
    )
}