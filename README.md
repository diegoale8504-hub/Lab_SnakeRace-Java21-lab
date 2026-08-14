# Snake Race — ARSW Lab #2 (Java 21, Virtual Threads)

**Escuela Colombiana de Ingeniería – Arquitecturas de Software**  
Laboratorio de programación concurrente: condiciones de carrera, sincronización y colecciones seguras.

---

## Requisitos

- **JDK 21** (Temurin recomendado)
- **Maven 3.9+**
- SO: Windows, macOS o Linux

---

## Cómo ejecutar

```bash
mvn clean verify
mvn -q -DskipTests exec:java -Dsnakes=4
```

- `-Dsnakes=N` → inicia el juego con **N** serpientes (por defecto 2).
- **Controles**:
  - **Flechas**: serpiente **0** (Jugador 1).
  - **WASD**: serpiente **1** (si existe).
  - **Espacio** o botón **Action**: Pausar / Reanudar.

---

## Reglas del juego (resumen)

- **N serpientes** corren de forma autónoma (cada una en su propio hilo).
- **Ratones**: al comer uno, la serpiente **crece** y aparece un **nuevo obstáculo**.
- **Obstáculos**: si la cabeza entra en un obstáculo hay **rebote**.
- **Teletransportadores** (flechas rojas): entrar por uno te **saca por su par**.
- **Rayos (Turbo)**: al pisarlos, la serpiente obtiene **velocidad aumentada** temporal.
- Movimiento con **wrap-around** (el tablero “se repite” en los bordes).

---

## Arquitectura (carpetas)

```
co.eci.snake
├─ app/                 # Bootstrap de la aplicación (Main)
├─ core/                # Dominio: Board, Snake, Direction, Position
├─ core/engine/         # GameClock (ticks, Pausa/Reanudar)
├─ concurrency/         # SnakeRunner (lógica por serpiente con virtual threads)
└─ ui/legacy/           # UI estilo legado (Swing) con grilla y botón Action
```

---

# Actividades del laboratorio

## Parte I — (Calentamiento) `wait/notify` en un programa multi-hilo

1. Toma el programa [**PrimeFinder**](https://github.com/ARSW-ECI/wait-notify-excercise).
2. Modifícalo para que **cada _t_ milisegundos**:
   - Se **pausen** todos los hilos trabajadores.
   - Se **muestre** cuántos números primos se han encontrado.
   - El programa **espere ENTER** para **reanudar**.
3. La sincronización debe usar **`synchronized`**, **`wait()`**, **`notify()` / `notifyAll()`** sobre el **mismo monitor** (sin _busy-waiting_).
4. Entrega en el reporte de laboratorio **las observaciones y/o comentarios** explicando tu diseño de sincronización (qué lock, qué condición, cómo evitas _lost wakeups_).

> Objetivo didáctico: practicar suspensión/continuación **sin** espera activa y consolidar el modelo de monitores en Java.

---

## Parte II — SnakeRace concurrente (núcleo del laboratorio)

### 1) Análisis de concurrencia

#### A. ¿Cómo el código usa hilos para dar autonomía a cada serpiente?
* **Hilos Virtuales (Java 21) por cada serpiente:** En `SnakeApp.java`, se inicializa un ejecutor de hilos virtuales mediante `Executors.newVirtualThreadPerTaskExecutor()`. Para cada serpiente creada, se envía una tarea `SnakeRunner` al ejecutor (`exec.submit(new SnakeRunner(s, board))`). De este modo, cada serpiente cuenta con su propio hilo virtual de ejecución independiente.
* **Ciclo de ejecución autónomo (`SnakeRunner.java`):** Cada hilo ejecuta un bucle continuo `while (!Thread.currentThread().isInterrupted())` donde:
  1. Decide aleatoriamente si realiza un giro (`maybeTurn()`) usando `ThreadLocalRandom`.
  2. Ejecuta su avance sobre el tablero compartido (`board.step(snake)`).
  3. Reacciona a colisiones o ítems consumidos (rebote en obstáculos o activación de turbo).
  4. Suspende su propio hilo temporalmente (`Thread.sleep(sleep)` con 80 ms base o 40 ms en turbo).
* **Interacción con otros hilos del sistema:**
  * **Event Dispatch Thread (EDT) de Swing:** Gestiona la ventana, captura eventos de teclado/botones y ejecuta el repintado en `GamePanel.paintComponent()`.
  * **Hilo de reloj (`GameClock.java`):** Emplea un `ScheduledExecutorService` que programa a intervalos fijos (60 ms) llamadas a `SwingUtilities.invokeLater(gamePanel::repaint)` para actualizar la interfaz.

---

#### B. Posibles Condiciones de Carrera (Race Conditions)
1. **Acceso concurrente al cuerpo de la serpiente (`Snake.body` vs `paintComponent`):**
   * El hilo virtual de la serpiente modifica la estructura `body` en `Snake.advance(...)` (`addFirst`, `removeLast`).
   * Al mismo tiempo, el hilo de la interfaz gráfica (EDT) itera sobre el cuerpo al invocar `snake.snapshot()` dentro de `paintComponent`.
   * **Riesgo:** `ArrayDeque` no está sincronizada ni es thread-safe, lo que puede provocar `ConcurrentModificationException`, lecturas con datos corruptos (*data tearing*) o inconsistencias visuales.
2. **Desconexión entre el control de pausa y los hilos de las serpientes:**
   * Al accionar el botón de pausa en `SnakeApp.togglePause()`, únicamente se pausa el reloj de la UI (`clock.pause()`).
   * Los hilos de las serpientes en `SnakeRunner` continúan corriendo en segundo plano sin detenerse. Al reanudar el juego, las serpientes ya han avanzado múltiples posiciones.
   * Si se intentan recolectar estadísticas en el momento de pausa (serpiente viva más larga o primera en morir), se leerán datos en constante cambio, generando lecturas inconsistentes.
3. **Modificación de dirección vs Avance:**
   * La UI o la IA invocan `snake.turn(dir)` modificando `direction`, mientras que `board.step(snake)` lee la dirección y la cabeza de forma no atómica con respecto a la ejecución de `snake.advance()`.

---

#### C. Colecciones o estructuras no seguras en contexto concurrente
1. **`ArrayDeque<Position> body` en `Snake.java`:**
   * No es una colección sincronizada ni concurrente. No admite lecturas y escrituras simultáneas desde hilos distintos.
2. **`ArrayList<Snake> snakes` en `SnakeApp.java`:**
   * Es una lista estándar mutable. Si durante el juego se agregan o remueven serpientes (por ejemplo, al morir o reiniciar) mientras el EDT itera sobre ella en `paintComponent`, se lanzará `ConcurrentModificationException`.
3. **`HashSet` y `HashMap` en `Board.java` (`mice`, `obstacles`, `turbo`, `teleports`):**
   * No son colecciones concurrentes. Aunque `Board` sincroniza sus métodos, las operaciones de lectura en la UI extraen copias defensivas completas en cada frame a 60 FPS, generando alto impacto en memoria y contención sobre el cerrojo global del tablero.

---

#### D. Ocurrencias de espera activa (busy-wait) o sincronización innecesaria
1. **Bloqueo grueso (*Coarse-grained locking*) en `Board.java`:**
   * Todo el método `board.step(Snake snake)` está marcado como `synchronized` sobre la instancia de `Board`.
   * **Impacto:** Con un número elevado de serpientes ($N \ge 20$), todas las serpientes se bloquean entre sí esperando el mismo monitor global solo para calcular un movimiento, serializando la ejecución y perdiendo los beneficios de la concurrencia.
2. **Contención por copias defensivas en el repintado:**
   * En cada cuadro de renderizado, `GamePanel.paintComponent` adquiere el cerrojo de `Board` repetidamente para `obstacles()`, `mice()`, `turbo()` y `teleports()`, compitiendo directamente con los hilos de las serpientes que intentan invocar `step()`.
3. **Búsqueda aleatoria bloqueante en `randomEmpty()`:**
   * Dentro del método sincronizado `step()`, al comer un ratón se invoca `randomEmpty()`, el cual ejecuta un bucle `do ... while` buscando casillas libres aleatoriamente mientras retiene el cerrojo de todo el tablero.
4. **Ausencia de mecanismo de suspensión pasiva para Pausa:**
   * No existe una estructura de coordinación pasiva (`wait()`/`notifyAll()` o `ReentrantLock` con `Condition`) para pausar los hilos de las serpientes, lo que obligaría erróneamente a implementar *busy-waiting* si no se introduce la sincronización adecuada.

### 2) Correcciones mínimas y regiones críticas

#### A. Eliminación de esperas activas y suspensión pasiva
* **Mecanismo de monitor (`wait()` / `notifyAll()`):** Se implementó el método `awaitIfPaused()` en `GameClock.java`. Los hilos virtuales de `SnakeRunner` consultan este método en cada ciclo antes de avanzar.
* **Comportamiento sin *busy-waiting*:** Mientras el estado del juego sea `PAUSED`, los hilos entran en suspensión pasiva mediante `pauseLock.wait()` liberando el procesador. Al reanudar (`clock.resume()`), se invoca `pauseLock.notifyAll()`, reactivando inmediatamente todas las serpientes.

#### B. Reducción de granularidad de bloqueo (*Fine-Grained Locking*) y colecciones concurrentes
* **Tablero (`Board.java`):** Se eliminó el modificador `synchronized` a nivel de todo el método `step(Snake)` y de los métodos de consulta. Las colecciones de `mice`, `obstacles` y `turbo` se migraron a `ConcurrentHashMap.newKeySet()`, y `teleports` a `ConcurrentHashMap`. Esto elimina la contención global entre las $N$ serpientes y entre la UI y los hilos.
* **Cuerpo de la serpiente (`Snake.java`):** Se protegieron con `synchronized` exclusivamente los accesos a `body` (`advance`, `head`, `snapshot`, `length`, `contains`), garantizando que la UI pueda extraer instantáneas seguras (`snapshot()`) sin generar `ConcurrentModificationException` ni lecturas corruptas (*data tearing*).
* **Registro seguro del orden de defunciones:** Se utiliza una lista concurrente `CopyOnWriteArrayList<Snake>` en `Board` para registrar de manera atómica e inmutable el orden exacto en que las serpientes colisionan y mueren (`recordDeath()`).

---

### 3) Control de ejecución seguro (UI)

#### A. Ciclo de Vida: Iniciar / Pausar / Reanudar
* **Máquina de estados integrada:** Se implementó el flujo completo de tres estados en `SnakeApp.java`:
  * **Iniciar:** Estado inicial `STOPPED`. Al presionar el botón *"Iniciar"* (o barra espaciadora), se lanzan los hilos virtuales de cada serpiente y se arranca el `GameClock`.
  * **Pausar:** Estado `RUNNING` pasa a `PAUSED`. Suspende el reloj de repintado y pone en espera pasiva a los hilos de las serpientes.
  * **Reanudar:** Estado `PAUSED` regresa a `RUNNING`. Se reactivan los hilos mediante `notifyAll()` y se continúa el repintado a 60 FPS.

#### B. Visualización de estadísticas consistentes sin *data tearing*
* Al entrar en pausa, se realiza una consulta sincronizada sobre el estado consolidado de las serpientes:
  1. **🏆 Serpiente viva más larga:** Se obtiene mediante `snakes.stream().filter(Snake::isAlive).max(Comparator.comparingInt(Snake::length))`, mostrando su ID, longitud en casillas y coordenadas actuales de la cabeza.
  2. **💀 Peor serpiente (la que primero murió):** Se consulta la primera posición de `board.deathOrder()`, indicando su ID y longitud final al momento del impacto (o informando si ninguna ha muerto).
  3. **Resumen de carrera:** Conteo de serpientes activas vs eliminadas.
* Se despliega un diálogo emergente con formato claro y estilizado. Al reanudar, la partida continúa desde el punto exacto de suspensión.

### 4) Robustez bajo carga

- Ejecuta con **N alto** (`-Dsnakes=20` o más) y/o aumenta la velocidad.
- El juego **no debe romperse**: sin `ConcurrentModificationException`, sin lecturas inconsistentes, sin _deadlocks_.
- Si habilitas **teleports** y **turbo**, verifica que las reglas no introduzcan carreras.

> Entregables detallados más abajo.

---

## Entregables

1. **Código fuente** funcionando en **Java 21**.
2. Todo de manera clara en **`**el reporte de laboratorio**`** con:
   - Data races encontradas y su solución.
   - Colecciones mal usadas y cómo se protegieron (o sustituyeron).
   - Esperas activas eliminadas y mecanismo utilizado.
   - Regiones críticas definidas y justificación de su **alcance mínimo**.
3. UI con **Iniciar / Pausar / Reanudar** y estadísticas solicitadas al pausar.

---

## Criterios de evaluación (10)

- (3) **Concurrencia correcta**: sin data races; sincronización bien localizada.
- (2) **Pausa/Reanudar**: consistencia visual y de estado.
- (2) **Robustez**: corre **con N alto** y sin excepciones de concurrencia.
- (1.5) **Calidad**: estructura clara, nombres, comentarios; sin _code smells_ obvios.
- (1.5) **Documentación**: **`reporte de laboratorio`** claro, reproducible;

---

## Tips y configuración útil

- **Número de serpientes**: `-Dsnakes=N` al ejecutar.
- **Tamaño del tablero**: cambiar el constructor `new Board(width, height)`.
- **Teleports / Turbo**: editar `Board.java` (métodos de inicialización y reglas en `step(...)`).
- **Velocidad**: ajustar `GameClock` (tick) o el `sleep` del `SnakeRunner` (incluye modo turbo).

---

## Cómo correr pruebas

```bash
mvn clean verify
```

Incluye compilación y ejecución de pruebas JUnit. Si tienes análisis estático, ejecútalo en `verify` o `site` según tu `pom.xml`.

---

## Créditos

Este laboratorio es una adaptación modernizada del ejercicio **SnakeRace** de ARSW. El enunciado de actividades se conserva para mantener los objetivos pedagógicos del curso.

**Base construida por el Ing. Javier Toquica.**
