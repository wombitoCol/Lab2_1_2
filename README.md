  # Segunda parte de la primera laboratorio ARSW
  ## Primer punto
  ### Analisis codigo 
Al revisar el código del juego, tenemos la clase main que llama a SnakeApp. SnakeApp es la que crea el juego por medio de un tablero y serpientes. Las serpientes, que se ubican en una ArrayList, son llamadas cada una a pasar por una clase nombrada SnakeRunner, el cual se asegura del movimiento de las serpientes y es un hilo.

En su método run() tenemos que, mientras el hilo no sea interrumpido, este llame a un método maybeTurn() (usado para que la serpiente haga un giro random), el cual utiliza ThreadLocalRandom.current().nextDouble(); esto con el fin de que los hilos no llamen todos a una misma instancia, en caso de usar random(), y se bloqueen esperando que les den un número random.

Luego de eso, le pide a board que dé ese paso. En este momento chequea dos cosas:

- Si hay un obstáculo, que haga otro giro random.
- Si se comió un turbo, que la variable del objeto turboTicks sea 100.

El caso de que se come un turbo es más especial, ya que, luego de setear turboTicks a 100, en una variable del método va verificando si turboTicks es mayor a 0: si el resultado es que sí, resta 1 a turboTicks y le da al período de dormida del hilo unos 40 milisegundos; si el resultado es que no, deja de restar en turboTicks y pone en sleep el valor por defecto, que es 80, en la dormida del hilo. Hay que entender que los hilos son dormidos ya que esto da unas pautas para que realicen movimientos en general; por eso, al reducir el tiempo de dormido del hilo, estos se mueven más erráticos y rápidos.

La clase Board también tiene métodos synchronized en la colocación y posición de ratones, obstáculos, step (movimientos) y turbos. Esto, con el objetivo de que la visibilidad de estos estados y su respectiva colocación, todos los hilos sincronizadamente los puedan consultar y modificar sin datos incorrectos.

Lo último que podemos ver relacionado a la concurrencia es que el atributo direction es un volatile, y esto ayuda a la modificación de este atributo, ya que es utilizado por varios hilos.
  ### Posibles condiciones de carrera
Puede producise una condicion de carrera en los siguientes casos:
- La variable direction en Snake esta creando una condicion de carrera, ya que esta siendo modificcada por dos hilos al mismo tiempo (snakeRunner y snakeApp), ya que randomTurn() de snakeRunner modifica la direccion y los keyListener de SnakeApp cambian la direccion de la serpiente y esto entra dentro del step en board.
### Colecciones o estructuras no seguras en contexto concurrente.
Pueden ser no seguras las siguientes estructuras:
- En Snake su cuerpo es una ArrayDeque la cual es llamada en board verificando cada vez que se mueva si se comio un raton para crecer. Y ya que con SnakeRunner cada hilo mueve el raton, esto genera que muchos estados de cambio de la serpiente al comerce un raton se cambie su tamaño en la arraylist pero el snapshot que da el tamaño de la serpiente y utilizado por SnakeApp genere un error ya que esta utilizado por dos hilos (SnakeApp y SnakeRunner).
### Ocurrencias de espera activa (busy-wait) o de sincronización innecesaria
Una busy-wait claro esta en randomEmpty ya que este buque corre a toda hora hasta que el guard lo bloquee y ya que este es utilizado por step y step lo utliza el hilo de las serpientes estas cada vez que comen una mice y verifica posiciones aleatorias para ver si hay un mice o teleport o etc. 
## Punto 3 — Iniciar/Pausar/Reanudar sin tearing

Descubrí algo importante: el GameClock original solo pausaba el repintado, no el movimiento. Los SnakeRunner corren en su propio while independiente del clock, así que presionar "Action" nunca detenía realmente a las serpientes — un bug de fondo que había que arreglar para que Pausar tuviera sentido.

Solución: creé PauseController, que coordina la pausa sin busy-wait:

Cada SnakeRunner, al final de cada iteración (fuera de cualquier lock), llama a checkPausePoint(). Si está en pausa, se bloquea con monitor.wait() — un wait bloqueante real, no un spin.
Cuando la UI pide pausar, arma un CountDownLatch con el número de serpientes vivas y espera (con timeout) a que todas confirmen que ya quedaron bloqueadas (cada una hace countDown() justo antes de entrar al wait).
Solo cuando el latch llega a 0 —es decir, ningún hilo puede seguir mutando una serpiente— la UI lee longestAlive/firstDead. Así evitas el tearing: nunca lees una serpiente a medio mover.

También aproveché para blindar Snake: antes body (un ArrayDeque, no thread-safe) se mutaba en advance() y se leía en snapshot()/pintado desde otro hilo sin ningún lock propio de la clase — solo Board.step() era synchronized, lo cual no protegía la lectura del panel de dibujo. Ahora todo pasa por un bodyLock interno.

La UI quedó con un solo botón que cicla Iniciar → Pausar → Reanudar → Pausar..., y una etiqueta que al pausar muestra algo como:

Mejor: S2 (longitud 9) | Peor: S0 (murió primero, orden 1)

## Punto 4 — Robustez con N alto

Con los cambios anteriores, corriendo con -Dsnakes=25 o más no debería haber ConcurrentModificationException porque:

Board.mice()/obstacles()/turbo()/teleports() ya devolvían copias synchronized (esto ya estaba bien en el código base).
Snake.snapshot() y Snake.length() ahora también están protegidos, que era el hueco real.
El randomEmpty() con su guard acotado no es en realidad una espera activa de concurrencia (no gira esperando que otro hilo cambie algo compartido bajo contención) — es un retry acotado para generar una posición libre. Vale la pena aclarar esto en el reporte para no confundirlo con el busy-wait que sí eliminamos (el de pausa).
Verifiqué que pauseController.checkPausePoint() se llama sin tener tomado el lock de Board ni el de Snake, así se evita cualquier posibilidad de deadlock entre el lock de pausa y las regiones críticas de movimiento.

<img width="1123" height="651" alt="image" src="https://github.com/user-attachments/assets/fa9ef08f-c08e-4ac9-bf3e-0a019051621b" />

Comeinzo juego con 25 serpientes

<img width="721" height="678" alt="image" src="https://github.com/user-attachments/assets/7cd3877f-6da8-4430-8ae1-42bbb0ce1fb7" />

Juego corriendo normalmente

<img width="743" height="673" alt="image" src="https://github.com/user-attachments/assets/5a1b656c-db6c-4563-9da6-5d37db63beae" />
Funcion de pausa funcionando normalmente y mostrando la primera serpiente en morir

