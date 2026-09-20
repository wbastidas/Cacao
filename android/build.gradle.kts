// Cada módulo declara los plugins que usa, en vez de declararlos aquí con
// "apply false". Así :nucleo se puede compilar y probar sin que Gradle tenga
// que resolver el plugin de Android, que es lo que permite ejecutar los tests
// de las reglas de negocio en cualquier máquina, sin el SDK de Android:
//
//     gradle :nucleo:test --configure-on-demand
