package io.github.chamsser.gymvi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GymviServerApplication

fun main(args: Array<String>) {
    runApplication<GymviServerApplication>(*args)
}
