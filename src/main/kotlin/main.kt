import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dto.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.lang.Exception
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val gson = Gson()
private const val BASE_URL = "http://192.168.0.11:9999"
private val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor(::println).apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()

fun main() {
    with(CoroutineScope(EmptyCoroutineContext)) {
        launch {
            try {
                val posts = getPosts(client)

                val postsWithComments = posts
                    .map { post ->
                        async {
                            PostWithComments(post, getComments(client, post.id))
                        }
                    }.awaitAll()

                val fullPosts = postsWithComments
                    .map { postsWithComments ->
                        async {
                            FullPost(post = postsWithComments.post,
                                postAuthor = getAuthor(client, postsWithComments.post.authorId),
                                comments = postsWithComments.comments,
                                commentsAuthors = postsWithComments.comments.map { comment ->
                                    getAuthor(client, comment.authorId)
                                })
                        }
                    }.awaitAll()

                println(fullPosts)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    Thread.sleep(30_000L)
}

suspend fun OkHttpClient.apiCall(url: String): Response {
    return suspendCoroutine { continuation ->
        Request.Builder()
            .url(url)
            .build()
            .let(::newCall)
            .enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            })
    }
}

suspend fun <T> makeRequest(url: String, client: OkHttpClient, typeToken: TypeToken<T>): T =
    withContext(Dispatchers.IO) {
        client.apiCall(url)
            .let { response ->
                if (!response.isSuccessful) {
                    response.close()
                    throw RuntimeException(response.message)
                }
                val body = response.body ?: throw RuntimeException("response body is null")
                gson.fromJson(body.string(), typeToken.type)
            }
    }

suspend fun getPosts(client: OkHttpClient): List<Post> =
    makeRequest("$BASE_URL/api/slow/posts", client, object : TypeToken<List<Post>>() {})


suspend fun getAuthor(client: OkHttpClient, id: Long): Author =
    makeRequest("$BASE_URL/api/authors/$id", client, object : TypeToken<Author>() {})

suspend fun getComments(client: OkHttpClient, id: Long) =
    makeRequest("$BASE_URL/api/posts/$id/comments", client, object : TypeToken<List<Comment>>() {})