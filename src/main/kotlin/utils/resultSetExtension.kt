package utils

import java.sql.ResultSet

fun ResultSet.getNullableBoolean(columnName: String): Boolean? {
    val result = this.getBoolean(columnName)
    return if (this.wasNull()) null else result
}
