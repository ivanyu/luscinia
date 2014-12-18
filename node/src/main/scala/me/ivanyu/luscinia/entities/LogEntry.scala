package me.ivanyu.luscinia.entities

/**
 * Node log operation
 */
sealed trait LogOperation
case object EmptyOperation extends LogOperation
case object SomeOperation extends LogOperation

/**
 * Log entry
 * @param term term of entry
 * @param operation operation
 */
case class LogEntry(term: Term, operation: LogOperation)
