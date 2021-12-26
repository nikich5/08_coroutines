package dto

data class PostWithComments (
    val post: Post,
    val comments: List<Comment>,
    )

data class FullPost(
    val post: Post,
    val postAuthor: Author,
    val comments: List<Comment>,
    val commentsAuthors: List<Author>
)