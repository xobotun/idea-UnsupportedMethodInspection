fun main() {
    val normal = NormalImplementation()
    normal.doSomething() // Works flawlessly

    val broken = IncompleteImplementation()
    broken.doSomething() // Suddenly throws exception, but you'll probably know this only at runtime.
}
