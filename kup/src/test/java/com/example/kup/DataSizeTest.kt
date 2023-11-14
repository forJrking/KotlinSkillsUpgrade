package com.example.kup

import com.example.kup.DataSize.Companion.bytes
import com.example.kup.DataSize.Companion.gb
import com.example.kup.DataSize.Companion.mb
import com.example.kup.DataSize.Companion.pb
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * @description:
 * @author: forjrking
 * @date: 2023/11/14 21:13
 */
class DataSizeTest {
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

    @Test()
    fun data_size1() {
        val bytes = 1.bytes
        val dataSize = 10.pb
        println("format bytes:$dataSize")
        //1125899906842600
        println("format gb:${dataSize.toString(DataUnit.GIGABYTES)}")
        //  format bytes:536870912B
        println("format b:${dataSize.inWholeBytes}")
    }

    @Test(expected = ArithmeticException::class)
    fun data_size_overflow() {
        10000.pb
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