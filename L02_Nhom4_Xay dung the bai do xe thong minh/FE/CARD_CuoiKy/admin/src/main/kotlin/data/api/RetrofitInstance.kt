package data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("http://127.0.0.1:8080")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    val loginApi: LoginApi by lazy { retrofit.create(LoginApi::class.java) }
    val cardApi: CardApi by lazy { retrofit.create(CardApi::class.java) }
}
//http://127.0.0.1:8080