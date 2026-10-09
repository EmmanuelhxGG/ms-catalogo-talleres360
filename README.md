# Microservicio de Catálogo de Talleres360

Servicio independiente de productos, precios y existencias. Java 17, Spring Boot 3.5.6, JPA, validación y Lombok. Puerto **8082**; PostgreSQL **catalog_db** en Compose y H2 en desarrollo. Rama **`backend-emmanuel`**. [Repositorio](https://github.com/EmmanuelhxGG/ms-catalogo-talleres360). Estado documentado al 6 de octubre de 2026.

## Responsabilidad

BFF consume productos para Operador/Admin; Órdenes consulta precio/stock y envía asignaciones de repuestos. El navegador no llama a este servicio directamente. Catálogo es dueño del precio y del stock, que es compartido por producto, no separado por taller.

Todas las rutas requieren `X-Internal-Key` coincidente con la configuración del servidor. El BFF permite lectura a Operador/Admin y escritura solo a Admin.

## Estructura

Las rutas Java parten de `src/main/java/com/talleres360/catalog/`.

| Ruta | Contenido |
| --- | --- |
| `controller/ProductController.java` | Productos y endpoints internos de stock; valida clave. |
| `service/CatalogService.java` | Reglas de catálogo, DTO de entrada/salida y asignación transaccional. |
| `model/Product.java` | SKU, nombre, precio, stock libre, activo y versión JPA. |
| `model/ReservaStock.java` | Última revisión y cantidades asignadas a una orden. |
| `model/StockConsumption.java` | Marcador idempotente del protocolo anterior. |
| `repository/` | Productos, reservas, consumos y bloqueos de escritura. |
| `src/main/resources/application.yml` | Puerto, clave y perfiles local/postgres. |
| `compose.yml`, `Dockerfile`, `.env.example` | Construcción y despliegue independientes. |

## API

| Método/ruta | Función |
| --- | --- |
| `GET /api/products` | Catálogo ordenado por nombre, incluyendo actividad y disponibilidad. |
| `GET /api/products/{id}` | Producto por ID. |
| `POST /api/products` | Crear; 201. |
| `PUT /api/products/{id}` | Actualizar datos, precio, stock libre y actividad. |
| `PUT /internal/stock-reservations` | Aplicar asignación completa por orden/revisión; 204. |
| `GET /internal/stock-reservations/{orderId}` | Revisión confirmada y cantidades; orden sin asignación devuelve revisión 0 y mapa vacío. |
| `POST /internal/stock-consumptions` | Consumo idempotente de entregas emitidas por la versión anterior; 204. |

La creación/edición recibe:

```json
{
  "sku": "FILTRO-001",
  "name": "Filtro de aceite",
  "price": 12000,
  "stock": 5,
  "active": true
}
```

La respuesta incluye `id`, `sku`, `name`, `price`, `stock`, `active` y `available`. Disponibilidad significa activo y stock libre mayor que cero. SKU se guarda en mayúsculas, sin espacios extremos, y es único. Nombre/SKU son obligatorios, precio no negativo y stock entero no negativo.

La baja es lógica: actualizar `active=false` conserva el producto y sus referencias. Un repuesto ya asignado puede mantenerse/devolverse aunque se desactive; no puede aumentarse su asignación.

## Qué significa stock

`stock` representa **unidades libres**. Las cantidades asignadas a órdenes aceptadas se guardan aparte en `reservas_stock` y `reserva_stock_items`.

Ejemplo: producto con 10 unidades libres; aceptar una orden con 2 deja 8. Editarla a 5 descuenta 3 más y deja 5. Reducirla a 1 devuelve 4 y deja 9. Cancelarla libera la unidad restante y deja 10. Entregar no descuenta otra vez.

Eliminar una orden ya entregada no devuelve repuestos consumidos. Las cantidades históricas de la orden entregada se conservan en la asignación registrada.

## Contrato de asignación versionada

```json
{
  "orderId": 101,
  "revision": 1,
  "items": [
    { "productId": 1, "quantity": 2 }
  ]
}
```

La lista es la **asignación completa deseada**, no un incremento. Para liberar se envía una revisión superior con `items: []`.

- La revisión aumenta por orden desde Órdenes.
- Revisiones iguales o anteriores se ignoran; un reintento no vuelve a descontar.
- Una cancelación con revisión superior protege frente a eventos antiguos.
- Los productos repetidos se suman.
- Se bloquea la reserva y los productos en orden de ID.
- Todas las diferencias se aplican juntas o se revierte toda la transacción.
- Aumentar requiere producto activo y suficiente stock libre.
- IDs/cantidades positivos; hasta 200 líneas y 1.000.000 unidades por línea.

La confirmación de stock forma parte del outbox asíncrono de Órdenes. Si Catálogo no confirma, Órdenes rechaza la entrega hasta confirmar su revisión requerida. Una corrección posterior puede sustituir una asignación que no pudo aplicarse.

El protocolo anterior recibe `eventId`, `orderId` e `items`: repetir el mismo evento no duplica consumo. Se conserva para procesar entregas antiguas, no para descontar nuevas entregas.

## Configurar .env

Crea `.env` junto a `compose.yml`:

```dotenv
DB_USERNAME=talleres360
DB_PASSWORD=<CONTRASENA_DE_ESTA_BASE>
INTERNAL_API_KEY=<CLAVE_COMPARTIDA_CON_BFF_Y_MICROS>
```

No necesita IDs de Azure ni URL del frontend. Compose configura `SERVER_PORT=8082`, perfil postgres y `jdbc:postgresql://postgres:5432/catalog_db`. `.env` lo carga Compose; Java directo necesita variables exportadas.

Órdenes y BFF deben configurar `CATALOG_URL=http://<IP_PRIVADA_DE_ESTA_EC2>:8082`, sin `/api` ni `/dev`.

## Construir y ejecutar

Requiere Git, Docker Engine, Buildx y Compose en EC2. El Dockerfile utiliza Maven y Java 17 para construir y JRE 17 con usuario no root para ejecutar; no necesita otro repositorio ni Maven en el host.

```bash
docker buildx version
docker compose version
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs --tail=100 catalogo
```

El Compose levanta Catálogo y su PostgreSQL, con volumen `datos_postgres`, comprobación de salud de la base y reinicio `unless-stopped`. Publica 8082, no PostgreSQL.

Para desarrollo aislado, con JDK 17/Maven y `INTERNAL_API_KEY` exportada:

```bash
mvn spring-boot:run
```

Este repositorio no incluye Maven Wrapper. El perfil local usa H2 en memoria; detener el proceso descarta sus datos.

## EC2, actualización y datos

[Guía de despliegue independiente](DESPLIEGUE_EC2.md). Permitir 8082 solo desde los grupos BFF y Órdenes. Si cambia la IP privada, actualizar `CATALOG_URL` en ambos y recrearlos.

Con rama verificada, cambios publicados y sin conflictos locales:

```bash
git pull --ff-only origin backend-emmanuel
docker compose up -d --build
```

Para la versión de asignaciones actualizar Catálogo antes de Órdenes/frontend. JPA usa `ddl-auto=update` para crear/actualizar tablas. Conservar las reservas vacías: su revisión identifica movimientos ya superados. No reutilizar IDs de órdenes con reservas existentes. Restaurar las bases de Órdenes y Catálogo de forma coherente.

Editar un producto establece su stock libre absoluto y bloquea su fila; la versión JPA es interna. El cambio debe realizarse sobre datos actuales. Recrear contenedores conserva el volumen; **`docker compose down -v` lo elimina**. No se importa automáticamente un catálogo guardado en otro repositorio/base.

## Verificación

```bash
mvn -DskipTests package
```

La revisión local del 8 de octubre verificó clave interna, stock, concurrencia, rollback e idempotencia con H2, no EC2. Los archivos de pruebas no forman parte de esta versión. El comando anterior comprueba empaquetado, no el funcionamiento del despliegue.

401 indica clave incorrecta; 404 producto inexistente; 409 SKU duplicado o existencias insuficientes. No publicar `.env`, claves o tokens. `target/` es salida compilada, no código fuente para publicar.
