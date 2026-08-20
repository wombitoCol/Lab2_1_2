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
