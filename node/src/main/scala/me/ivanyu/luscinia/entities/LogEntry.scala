package me.ivanyu.luscinia.entities

sealed trait LogOperation
case object EmptyOperation extends LogOperation
case object SomeOperation extends LogOperation

case class LogEntry(term: Term, operation: LogOperation)