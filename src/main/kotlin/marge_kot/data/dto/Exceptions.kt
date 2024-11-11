package marge_kot.data.dto

class CannotMergeException(override val message: String): Throwable(message)
class NeedRebaseException : Throwable()
class RebaseErrorException(override val message: String): Throwable(message)