package com.example.reproductormusica

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// Tabla 1: las listas
@Entity(tableName = "listas")
data class ListaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String
)

// Tabla 2: qué canciones tiene cada lista.
// La clave doble (listaId + cancionId) impide repetir una canción en la misma lista.
@Entity(
    tableName = "lista_canciones",
    primaryKeys = ["listaId", "cancionId"],
    foreignKeys = [
        ForeignKey(
            entity = ListaEntity::class,
            parentColumns = ["id"],
            childColumns = ["listaId"],
            onDelete = ForeignKey.CASCADE // al borrar una lista se borran sus filas
        )
    ]
)
data class ListaCancionEntity(
    val listaId: Long,
    val cancionId: Long, // el _ID de MediaStore
    val posicion: Int
)

// Resultado de una consulta: una lista con su número de canciones
data class ListaConConteo(
    val id: Long,
    val nombre: String,
    val total: Int
)

@Dao
interface ListasDao {

    // Flow: la pantalla se actualiza sola cuando cambian los datos
    @Query(
        "SELECT l.id AS id, l.nombre AS nombre, COUNT(lc.cancionId) AS total " +
                "FROM listas l LEFT JOIN lista_canciones lc ON lc.listaId = l.id " +
                "GROUP BY l.id ORDER BY l.nombre COLLATE NOCASE"
    )
    fun observarListas(): Flow<List<ListaConConteo>>

    @Query("SELECT cancionId FROM lista_canciones WHERE listaId = :listaId ORDER BY posicion")
    fun observarIdsDeLista(listaId: Long): Flow<List<Long>>

    @Insert
    suspend fun crearLista(lista: ListaEntity): Long

    @Query("DELETE FROM listas WHERE id = :id")
    suspend fun eliminarLista(id: Long)

    // Añade al final; si ya estaba en la lista, no hace nada
    @Query(
        "INSERT OR IGNORE INTO lista_canciones (listaId, cancionId, posicion) " +
                "SELECT :listaId, :cancionId, COALESCE(MAX(posicion), -1) + 1 " +
                "FROM lista_canciones WHERE listaId = :listaId"
    )
    suspend fun agregarCancion(listaId: Long, cancionId: Long)

    @Query("DELETE FROM lista_canciones WHERE listaId = :listaId AND cancionId = :cancionId")
    suspend fun quitarCancion(listaId: Long, cancionId: Long)
}

@Database(
    entities = [ListaEntity::class, ListaCancionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listasDao(): ListasDao

    companion object {
        @Volatile
        private var instancia: AppDatabase? = null

        // Una sola base de datos para toda la app
        fun obtener(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "musica.db"
                ).build().also { instancia = it }
            }
    }
}