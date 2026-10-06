# ms-talleres360-catalog

Servicio Spring Boot independiente (puerto 8082, base `catalog_db`). Es dueño de productos, precios, existencias y movimientos de consumo. Orders ya no guarda productos propios; consulta este servicio para valorar repuestos y comprobar stock.

## Ejecutar

Este repositorio tiene su propio `compose.yml`: copia `.env.example` a `.env`, completa la contraseña de base de datos y la clave interna, y ejecuta `docker compose up -d --build`. Levanta solo Catálogo y su PostgreSQL persistente. Consulta [DESPLIEGUE_EC2.md](DESPLIEGUE_EC2.md) para configurar su EC2 independiente y las conexiones con Órdenes y BFF.

Para desarrollo aislado usa Java 17 y Maven instalado (`mvn spring-boot:run`); por defecto usa H2 en memoria y requiere `INTERNAL_API_KEY`. Este repositorio no incluye Maven Wrapper. El Dockerfile incluye Maven para compilar sin depender de otros repositorios. Con Maven instalado, ejecuta `mvn test` para las pruebas.

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
