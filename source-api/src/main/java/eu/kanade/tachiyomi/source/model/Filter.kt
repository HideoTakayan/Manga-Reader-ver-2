package eu.kanade.tachiyomi.source.model

sealed class Filter<T>(val name: String, var state: T) {
    open class Header(name: String) : Filter<Any>(name, Any())
    open class Separator(name: String = "") : Filter<Any>(name, Any())
    open class CheckBox(name: String, state: Boolean = false) : Filter<Boolean>(name, state)
    open class TriState(name: String, state: Int = 0) : Filter<Int>(name, state)
    open class Text(name: String, state: String = "") : Filter<String>(name, state)
    open class Select<V>(name: String, val values: Array<V>, state: Int = 0) : Filter<Int>(name, state)
    open class Group<T>(name: String, state: List<T>) : Filter<List<T>>(name, state)
    open class Sort(name: String, val values: Array<String>, state: Selection? = null) :
        Filter<Sort.Selection?>(name, state) {
        data class Selection(val index: Int, val ascending: Boolean)
    }
}

class FilterList(val list: List<Filter<*>>) : List<Filter<*>> by list {
    constructor(vararg filters: Filter<*>) : this(filters.toList())
}
