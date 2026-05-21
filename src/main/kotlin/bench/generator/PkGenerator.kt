package bench.generator

interface PkGenerator<T : Any> {
    val strategyName: String
    fun generate(): T
}

typealias BinaryPk = ByteArray
