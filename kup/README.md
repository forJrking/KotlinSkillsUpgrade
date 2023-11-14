---
theme: jzman
highlight: androidstudio
---
# 用Kotlin参照Duration来优化存储容量运算
## 前言
在之前的文章[《用Kotlin Duration来优化时间运算》](https://juejin.cn/post/7298899182165262371)
中，用Duration可以很方便的进行时间的单位换算和运算。我忽然想到平时的工作中经常用到的存储容量单位的换算和运算。
```kotlin
//进率为1024
val tenMegabytes = 10 * 1024 * 1024       //10mb
val tenGigabytes = 10 * 1024 * 1024 * 1024 //10gb
```
这样的业务代码加入了单位换算后阅读性就变差了，能否有像Duration一样的api实现下面这样的代码呢？
```kotlin
fun main() {
    val fiftyMegabytes = 50.mb
    val divValue = fiftyMegabytes - 30.mb
    // 20mb
    val timesValue = fiftyMegabytes * 2.4
    // 120mb

    // 1G文件 再增加2个50mb的数据空间
    val fileSpace = fiftyMegabytes * 2 + 1.gb
    RandomAccessFile("fileName","rw").use {
        it.setLength(fileSpace.inWholeBytes)
        it.write(...)
    }
}
```
## 简单拆解Duration
kotlin没有提供，要做到上面的api那么我不会啊，但是我看到Duration可以做到，那我们来看看它的原理，进行仿写就行了。

1. Duration有个`DurationUnit`是用来定义时间不同单位，方便换算和转换的。而存储容量的单位一般有`比特(b)，字节(B)，千字节(KB)，兆字节(MB)，千兆字节(GB)，太字节(TB)，拍字节(PB)，艾字节(EB)，泽字节(ZB)，尧字节(YB)`
，考虑到实际应用和Long的取值范围我们最大支持PB即可。
    ```kotlin
    enum class DataUnit(val shortName: String) {
        BYTES("B"),
        KILOBYTES("KB"),
        MEGABYTES("MB"),
        GIGABYTES("GB"),
        TERABYTES("TB"),
        PETABYTES("PB")
    }
    ```
2. Duration是如何做到不同单位的数据换算的，先看看Duration的创建函数和构造。`toDuration`把当前的值通过`convertDurationUnit`把时间换算成nanos或millis的值，再通过shl运算用来记录单位。
    ```kotlin
    //Long创建 Duration
    public fun Long.toDuration(unit: DurationUnit): Duration {
        //最大支持的 nanos值
        val maxNsInUnit = convertDurationUnitOverflow(MAX_NANOS, DurationUnit.NANOSECONDS, unit)
        //当前值如果在最大和最小值中间 表示不会溢出
        if (this in -maxNsInUnit..maxNsInUnit) {
        //创建 rawValue 是Nanos的 Duration
            return durationOfNanos(convertDurationUnitOverflow(this, unit, DurationUnit.NANOSECONDS))
        } else {
        //创建 rawValue 是millis的 Duration
            val millis = convertDurationUnit(this, unit, DurationUnit.MILLISECONDS)
            return durationOfMillis(millis.coerceIn(-MAX_MILLIS, MAX_MILLIS))
        }
    }
    // 用 nanos
    private fun durationOfNanos(normalNanos: Long) = Duration(normalNanos shl 1)
    // 用 millis
    private fun durationOfMillis(normalMillis: Long) = Duration((normalMillis shl 1) + 1)
    ```
3. Duration是一个value class用来提升性能的，通过`rawValue`还原当前时间换算后的nanos或millis的数据`value`。为何不全部都用Nanos省去了这些计算呢，根据代码看应该是考虑了Nanos的计算会溢出。用一个long值可以还原构造对象前的所有参数，这代码设计真牛逼。
    ```kotlin
    @JvmInline
    public value class Duration internal constructor(private val rawValue: Long) : Comparable<Duration> {
        //原始最小单位数据
        private val value: Long get() = rawValue shr 1
        //单位鉴别器
        private inline val unitDiscriminator: Int get() = rawValue.toInt() and 1
        private fun isInNanos() = unitDiscriminator == 0
        private fun isInMillis() = unitDiscriminator == 1
        //还原的最小单位 DurationUnit对象
        private val storageUnit get() = if (isInNanos()) DurationUnit.NANOSECONDS else DurationUnit.MILLISECONDS
    ```
4. Duration是如何做到算术运算的，是通过操作符重载实现的。不同单位Duration，持有的数据是同一个单位的那么是可以互相运算的，我们后面会着重介绍和仿写。
5. Duration是如何做到逻辑运算的(>,<,==)，构造函数实现了接口`Comparable<Duration>`重写了`operator fun compareTo(other: Duration): Int`,返回1，-1，0

> 总结一下Duration主要依靠对象内部持有的value: Long，由于value的单位是“相同”的，就可以实现不同单位的换算和运算。

## DataSize 创建和换算设计
1. 对于存储容量来说最小单位我们就定为Bytes，最大支持到PB，然后可以省去对数据过大的溢出的"单位鉴别器"设计。
    ```kotlin
    @JvmInline
    value class DataSize internal constructor(private val rawValue: Long)
    ```
2. 参照Duration在创建和最后单位换算时候都用到了`convertDurationUnit`函数，接受原始单位和目标单位。另外考虑到可能出现换算溢出使用`Math.multiplyExact`来抛出异常，防止数据计算异常无法追溯的问题。
    ```kotlin
    /** Bytes per Kilobyte.*/
    private const val BYTES_PER_KB: Long = 1024
    /** Bytes per Megabyte.*/
    private const val BYTES_PER_MB = BYTES_PER_KB * 1024
    /** Bytes per Gigabyte.*/
    private const val BYTES_PER_GB = BYTES_PER_MB * 1024
    /** Bytes per Terabyte.*/
    private const val BYTES_PER_TB = BYTES_PER_GB * 1024
    /** Bytes per PetaByte.*/
    private const val BYTES_PER_PB = BYTES_PER_TB * 1024

    internal fun convertDataUnit(value: Long, sourceUnit: DataUnit, targetUnit: DataUnit): Long {
        val valueInBytes = when (sourceUnit) {
            DataUnit.BYTES -> value
            DataUnit.KILOBYTES -> Math.multiplyExact(value, BYTES_PER_KB)
            DataUnit.MEGABYTES -> Math.multiplyExact(value, BYTES_PER_MB)
            DataUnit.GIGABYTES -> Math.multiplyExact(value, BYTES_PER_GB)
            DataUnit.TERABYTES -> Math.multiplyExact(value, BYTES_PER_TB)
            DataUnit.PETABYTES -> Math.multiplyExact(value, BYTES_PER_PB)
        }
        return when (targetUnit) {
            DataUnit.BYTES -> valueInBytes
            DataUnit.KILOBYTES -> valueInBytes / BYTES_PER_KB
            DataUnit.MEGABYTES -> valueInBytes / BYTES_PER_MB
            DataUnit.GIGABYTES -> valueInBytes / BYTES_PER_GB
            DataUnit.TERABYTES -> valueInBytes / BYTES_PER_TB
            DataUnit.PETABYTES -> valueInBytes / BYTES_PER_PB
        }
    }

    internal fun convertDataUnit(value: Double, sourceUnit: DataUnit, targetUnit: DataUnit): Double {
        val valueInBytes = when (sourceUnit) {
            DataUnit.BYTES -> value
            DataUnit.KILOBYTES -> value * BYTES_PER_KB
            DataUnit.MEGABYTES -> value * BYTES_PER_MB
            DataUnit.GIGABYTES -> value * BYTES_PER_GB
            DataUnit.TERABYTES -> value * BYTES_PER_TB
            DataUnit.PETABYTES -> value * BYTES_PER_PB
        }
        require(!valueInBytes.isNaN()) { "DataUnit value cannot be NaN." }
        return when (targetUnit) {
            DataUnit.BYTES -> valueInBytes
            DataUnit.KILOBYTES -> valueInBytes / BYTES_PER_KB
            DataUnit.MEGABYTES -> valueInBytes / BYTES_PER_MB
            DataUnit.GIGABYTES -> valueInBytes / BYTES_PER_GB
            DataUnit.TERABYTES -> valueInBytes / BYTES_PER_TB
            DataUnit.PETABYTES -> valueInBytes / BYTES_PER_PB
        }
    }
    ```
3. 扩展属性和构造DataSize，DataSize的rawValue是bytes因此所有的目标单位设置为DataUnit.BYTES，而原始单位就通过调用者告诉`convertDataUnit`
    ```kotlin
    fun Long.toDataSize(unit: DataUnit): DataSize {
        return DataSize(convertDataUnit(this, unit, DataUnit.BYTES))
    }
    fun Double.toDataSize(unit: DataUnit): DataSize {
        return DataSize(convertDataUnit(this, unit, DataUnit.BYTES).roundToLong())
    }
    inline val Long.bytes get() = this.toDataSize(DataUnit.BYTES)
    inline val Long.kb get() = this.toDataSize(DataUnit.KILOBYTES)
    inline val Long.mb get() = this.toDataSize(DataUnit.MEGABYTES)
    inline val Long.gb get() = this.toDataSize(DataUnit.GIGABYTES)
    inline val Long.tb get() = this.toDataSize(DataUnit.TERABYTES)
    inline val Long.pb get() = this.toDataSize(DataUnit.PETABYTES)

    inline val Int.bytes get() = this.toLong().toDataSize(DataUnit.BYTES)
    inline val Int.kb get() = this.toLong().toDataSize(DataUnit.KILOBYTES)
    inline val Int.mb get() = this.toLong().toDataSize(DataUnit.MEGABYTES)
    inline val Int.gb get() = this.toLong().toDataSize(DataUnit.GIGABYTES)
    inline val Int.tb get() = this.toLong().toDataSize(DataUnit.TERABYTES)
    inline val Int.pb get() = this.toLong().toDataSize(DataUnit.PETABYTES)

    inline val Double.bytes get() = this.toDataSize(DataUnit.BYTES)
    inline val Double.kb get() = this.toDataSize(DataUnit.KILOBYTES)
    inline val Double.mb get() = this.toDataSize(DataUnit.MEGABYTES)
    inline val Double.gb get() = this.toDataSize(DataUnit.GIGABYTES)
    inline val Double.tb get() = this.toDataSize(DataUnit.TERABYTES)
    inline val Double.pb get() = this.toDataSize(DataUnit.PETABYTES)
    ```
4. 换算函数设计
Duration用`toLong(DurationUnit)或者toDouble(DurationUnit)`来输出指定单位的数据，inWhole系列函数是对`toLong(DurationUnit)
`的封装。toLong和toDouble实现就比较简单了，把convertDataUnit输出单位指定为传入的参数，而原始单位就是rawValue的单位`DataUnit.BYTES`
toDouble需要输出更加精细的数据，例如: 512mb = 0.5gb。

    ```kotlin
    val inWholeBytes: Long
        get() = toLong(DataUnit.BYTES)
    val inWholeKilobytes: Long
        get() = toLong(DataUnit.KILOBYTES)
    val inWholeMegabytes: Long
        get() = toLong(DataUnit.MEGABYTES)
    val inWholeGigabytes: Long
        get() = toLong(DataUnit.GIGABYTES)
    val inWholeTerabytes: Long
        get() = toLong(DataUnit.TERABYTES)
    val inWholePetabytes: Long
        get() = toLong(DataUnit.PETABYTES)

    fun toDouble(unit: DataUnit): Double = convertDataUnit(bytes.toDouble(), DataUnit.BYTES, unit)
    fun toLong(unit: DataUnit): Long = convertDataUnit(bytes, DataUnit.BYTES, unit)
    ```

## 操作符设计
在Kotlin 中可以为类型提供预定义的一组操作符的自定义实现，被称为[操作符重载](https://book.kotlincn.net/text/operator-overloading.html)。这些操作符具有预定义的符号表示（如 + 或
*）与优先级。为了实现这样的操作符，需要为相应的类型提供一个指定名称的**成员函数或扩展函数**。这个类型会成为二元操作符左侧的类型及一元操作符的参数类型。

如果函数不存在或不明确，则导致编译错误（编译器会提示报错）。下面为常见操作符对照表：

|  操作符   | 函数名  | 说明  |
|  ----  | ----  |----  |
| +a    |  a.unaryPlus()   |   一元操作 取正
| -a	|  a.unaryMinus()  |   一元操作 取负
| !a	|  a.not()         |   一元操作 取反
| a + b |a.plus(b)   |   二元操作 加 |
| a - b |a.minus(b)  |   二元操作 减 |
| a * b |a.times(b)  |   二元操作 乘 |
| a / b |a.div(b)    |   二元操作 除 |

### 算术运算支持
1. 这里用算术运算符`+`实现来举例：假如`DataSize`对象需要重载操作符`+`，期望可以达到`val a = DataSize(); val c: DataSize = a + b`
2. 需要定义扩展函数`operator fun DataSize.plus(other: T): DataSize {...}`或者添加成员函数`operator fun plus(other: T): DataSize {...}`
3. 函数中的参数`other: T`表示b的对象类型，例如同类型`operator fun DataSize.plus(other: DataSize): DataSize {...}`
4. 在Duration中一般为了**阅读性**不会和同Duration类型的对象运算，而使用了`Int或Double`，因此重载运算符用了`public operator fun times(scale: Int): Duration`
5. 那么在DataSize中我们需要重载`+,-,*,/`，并且`*,/`重载的参数支持Int和Double即可
    ```kotlin
    operator fun unaryMinus(): DataSize {
        return DataSize(-this.bytes)
    }
    operator fun plus(other: DataSize): DataSize {
        return DataSize(Math.addExact(this.bytes, other.bytes))
    }

    operator fun minus(other: DataSize): DataSize {
        return this + (-other) // a - b = a + (-b)
    }

    operator fun times(scale: Int): DataSize {
        return DataSize(Math.multiplyExact(this.bytes, scale.toLong()))
    }

    operator fun div(scale: Int): DataSize {
        return DataSize(this.bytes / scale)
    }

    operator fun times(scale: Double): DataSize {
        return DataSize((this.bytes * scale).roundToLong())
    }

    operator fun div(scale: Double): DataSize {
        return DataSize((this.bytes / scale).roundToLong())
    }
    ```
    上面的操作符重载中`minus()`我们使用了 `plus()`和`unaryMinus()`重载组合`a - b = a + (-b)`，这样我们可以多一个`-DataSize`的操作符

### 逻辑运算支持
让DataSize构造函数实现了接口`Comparable<DataSize>`重写了`operator fun compareTo(other: DataSize): Int`,返回1，-1，0

   ```kotlin
    value class DataSize internal constructor(private val bytes: Long) : Comparable<DataSize> {
        override fun compareTo(other: DataSize): Int {
            return this.bytes.compareTo(other.bytes)
        }
   ```

## 获取字符串形式
为了方便打印和UI展示，一般我们需要重写`toSting`。Duration的`toSting`不需要指定输出单位，可以详细的输出当前对象的字符串格式（1h 0m 45.677s）算法比较复杂。我不太会，就简单实现指定输出单位的`toString(DataUnit)`
   ```kotlin
    fun toString(unit: DataUnit, decimals: Int = 2): String {
        require(decimals >= 0) { "decimals must be not negative, but was $decimals" }
        val number = toDouble(unit)
        if (number.isInfinite()) return number.toString()
        val newDecimals = decimals.coerceAtMost(12)
        return DecimalFormat("0").run {
            if (newDecimals > 0) minimumFractionDigits = newDecimals
            roundingMode = RoundingMode.HALF_UP
            format(number) + unit.shortName
        }
    }
   ```

## 单元测试
功能都写好了需要验证期望的结果和实现的功能是否一直，那么这个时候就用单元测试最好来个100%覆盖。
```kotlin
class ExampleUnitTest {
    @Test
    fun data_size() {
        val dataSize = 512.mb

        println("format bytes:$dataSize")
    //  format bytes:536870912B
        println("format kb:${dataSize.toString(DataUnit.KILOBYTES)}")
    //  format kb:524288.00KB
        println("format gb:${dataSize.toString(DataUnit.GIGABYTES)}")
    //  format gb:0.50GB
        // 单位换算
        assertEquals(512 * 1024 * 1024, dataSize.inWholeBytes)
        assertEquals(512 * 1024, dataSize.inWholeKilobytes)
        assertEquals(512, dataSize.inWholeMegabytes)
        assertEquals(0, dataSize.inWholeGigabytes)
        assertEquals(0, dataSize.inWholeTerabytes)
        assertEquals(0, dataSize.inWholePetabytes)
    }

    @Test
    fun data_size_operator() {
        val dataSize1 = 512.mb
        val dataSize2 = 3.gb

        val unaryMinusValue = -dataSize1 //取负数
        println("unaryMinusValue :${unaryMinusValue.toString(DataUnit.MEGABYTES)}")
    //  unaryMinusValue :-512.00MB

        val plusValue = dataSize1 + dataSize2     //+
        println("plus :${plusValue.toString(DataUnit.GIGABYTES)}")
    //  plus :3.50GB

        val minusValue = dataSize1 - dataSize2    // -
        println("minus :${minusValue.toString(DataUnit.GIGABYTES)}")
    //  minus :-2.50GB

        val timesValue = dataSize1 * 2      //乘法
        println("times :${timesValue.toString(DataUnit.GIGABYTES)}")
    //  times :1.00GB

        val divValue = dataSize2 / 2        //除法
        println("div :${divValue.toString(DataUnit.GIGABYTES)}")
    //  div :1.50GB
    }
}
```

## 总结
通过学习Kotlin Duration的api，举一反三应用到储存容量单位转换和运算中。Duration中的拆解计算api，还有toSting算法实现就留给其他同学实现吧。当然了你也可以实现和Duration更加精细的"单位鉴别器"设计，支持bit单位。

操作符重载文档： https://book.kotlincn.net/text/operator-overloading.html
