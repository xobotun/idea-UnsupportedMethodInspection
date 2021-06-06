// Check UField here instead of UMethod
private val oneNothingnessPlease = IncompleteImplementation().doSomething()

fun main() {
    // UExpression within UExpression!
    val lambda = {
        IncompleteImplementation().doSomething()
    }

    lambda.invoke()
}
