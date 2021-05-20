fun main() {
    startLokalApp()
}

fun startLokalApp(): App {
    val database = TestDatabase()
    val app = App(database.dataSource)

    app.start()

    return app
}
