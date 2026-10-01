# ms-talleres360-catalog

Servicio Spring Boot independiente (puerto 8082, base `catalog_db`). Es dueño de productos, precios, existencias y movimientos de consumo. Orders ya no guarda productos propios; consulta este servicio para valorar repuestos y comprobar stock.

## Ejecutar

En el stack completo: `docker compose -f infra/apps/compose.yml up -d --build` desde la raíz de `talleres360-backend`. En la EC2 backend: `docker compose -f infra/ms/compose.yml up -d --build`. Ambos crean PostgreSQL y volumen **propios del catálogo**. Configura `INTERNAL_API_KEY` en el `.env` de ese Compose y la misma clave en el BFF.

Para desarrollo aislado, arranca con Java 17 y Maven (`mvn spring-boot:run` desde esta carpeta); usa H2 en memoria por defecto y también requiere `INTERNAL_API_KEY`. Este módulo no tiene wrapper propio: si no tienes Maven global, desde su carpeta usa `../ms-talleres360-orders/mvnw -f pom.xml spring-boot:run` (Windows: `..\ms-talleres360-orders\mvnw.cmd -f pom.xml spring-boot:run`). El Dockerfile compila el proyecto sin depender del wrapper de orders. Ejecuta las pruebas sustituyendo `spring-boot:run` por `test`.

## API interna

Todas las llamadas requieren `X-Internal-Key`; en uso normal las realiza el BFF o orders, no el navegador.

| Método y ruta | Uso |
| --- | --- |
| `GET /api/products`, `GET /api/products/{id}` | Listar y consultar. Operador/Admin vía BFF. |
| `POST /api/products` | Crear. Solo Admin vía BFF. |
| `PUT /api/products/{id}` | Nombre, SKU, precio, stock y activo. Solo Admin vía BFF. |
| `POST /internal/stock-consumptions` | Descontar al entregar, desde el outbox de orders. |

Producto: `{ "sku": "FILTRO-001", "name": "Filtro", "price": 12000, "stock": 5, "active": true }`. SKU único; precio y stock no negativos. El consumo acepta `{ "eventId": "<UUID>", "orderId": 1, "items": [{"productId": 1, "quantity": 2}] }`; repetir `eventId` no vuelve a descontar. La transacción bloquea los productos y rechaza stock insuficiente.

**Pendiente para producción:** hoy el Admin puede ajustar stock mediante actualización del producto, sin historial de cada ajuste. Hay que añadir un libro de movimientos para entradas/correcciones con motivo, usuario y fecha. La consulta de stock al editar una orden no lo reserva; otra operación puede agotarlo antes de entregar. La entrega de órdenes y el descuento no son atómicos entre servicios; consulta el README raíz. El catálogo anterior almacenado en la base de orders no se importa automáticamente.
