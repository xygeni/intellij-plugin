package com.github.xygeni.intellij.events

/**
 * EventBus
 *
 * @author : Carmendelope
 * @version : 6/10/25 (Carmendelope)
 **/

typealias EventListener<T> = (T) -> Unit

object EventBus {
    private val listeners = mutableMapOf<String, MutableList<EventListener<Any?>>>()

    fun <T> subscribe(eventType: String, listener: EventListener<T>) {
        val list = listeners.getOrPut(eventType) { mutableListOf() }
        @Suppress("UNCHECKED_CAST")
        list.add(listener as EventListener<Any?>)
    }

    fun publish(eventType: String, payload: Any?) {
        listeners[eventType]?.forEach { it(payload) }
    }
}