package marge_kot.data.dto

class CannotMergeException(override val message: String): Throwable(message)
class NeedRebaseException : Throwable()