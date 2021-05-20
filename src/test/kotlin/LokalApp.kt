fun main() {
    startLokalApp()
}

fun startLokalApp(): App {
    val database = TestDatabase()
    val app = App(Service())

    app.start()

    return app
}
