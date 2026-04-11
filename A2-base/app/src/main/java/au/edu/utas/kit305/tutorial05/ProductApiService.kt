package au.edu.utas.kit305.tutorial05

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ProductApiService {

    @GET("product")
    suspend fun getAllProducts(): Response<ProductListResponse>

    @GET("product")
    suspend fun getProductsByCategory(
        @Query("category") category: String
    ): Response<ProductListResponse>

    @GET("product/{id}")
    suspend fun getProductById(
        @Path("id") id: String
    ): Response<ProductDetailResponse>
}
