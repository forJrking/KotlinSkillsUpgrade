package com.example.managespace.ui

import android.graphics.Paint
import androidx.compose.animation.core.FloatTweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private fun getPositionFromAngle(angles: List<Float>, touchAngle: Double, startAngle: Float): Int {
    var totalanagle = 0f
    for ((i, angle) in angles.withIndex()) {
        totalanagle += angle
        if (touchAngle >= startAngle && touchAngle <= (startAngle + totalanagle) % 360) {
            return i
        } else if (startAngle + totalanagle > 360) {
            if (touchAngle >= startAngle || touchAngle < (startAngle + totalanagle) % 360) {
                return i
            }
        }//角度计算
    }
    return -1
}

@Composable
fun PieChart(color: List<Color>, point: List<Float>, labels: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CenterPieChart(color, point, labels)
        LazyRow(
            modifier = Modifier.padding(30.dp, 10.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(labels, key = { _, item -> item }) { index, label ->
                Row(
                    modifier = Modifier
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color = color[index])
                    )
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 5.dp, end = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun Dp.dpToPx() = with(LocalDensity.current) { this@dpToPx.toPx() }

@Composable
fun Int.pxToDp() = with(LocalDensity.current) { this@pxToDp.toDp() }

@Composable
fun CenterPieChart(color: List<Color>, point: List<Float>, labels: List<String>) {
    val sum = point.sum()
    val configuration = LocalConfiguration.current
    val viewWeight = (configuration.screenWidthDp * 0.75).dp.dpToPx() //自定义View宽度 此处为正方形 所以宽高一致
    val drawHeight = 120f //内部线高 即有颜色部分的宽度
    val selecyAddHeight = 8f //点击后增加的高度
    val angles = mutableListOf<Float>()

    var start by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0) }
    var dragOffest by remember { mutableStateOf(0f) }
    val sweepPre by animateFloatAsState(
        targetValue = if (start) 1f else 0f,
        animationSpec = FloatTweenSpec(duration = 1000), label = ""
    )
    val paint = remember { Paint() }
    paint.color = Color.Black.toArgb()
    paint.textSize = 48f //中间文本字体大小
    paint.style = Paint.Style.STROKE
    Canvas(
        modifier = Modifier
            .size(
                viewWeight
                    .toInt()
                    .pxToDp()
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val x = it.x - viewWeight / 2
                        val y = it.y - viewWeight / 2
                        var touchAngle = Math.toDegrees(atan2(y.toDouble(), x.toDouble()))
                        //坐标1,2象限返回-180~0  3,4象限返回0~180
                        if (x < 0 && y < 0 || x > 0 && y < 0) {//1,2象限
                            touchAngle += 360
                        }

                        val newPosition =
                            getPositionFromAngle(
                                touchAngle = touchAngle,
                                angles = angles,
                                startAngle = dragOffest % 360
                            )
                        position = if (newPosition == position) {
                            -1
                        } else {
                            newPosition
                        }
//                        Toast
//                            .makeText(
//                                context,
//                                "onTap: $position",
//                                Toast.LENGTH_SHORT
//                            )
//                            .show()
                    }
                )
            }
            .pointerInput(Unit) {
                var dragstartx = 0f
                var dragstarty = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        // 拖动开始
                        dragstartx = offset.x
                        dragstarty = offset.y
                    },
                    onDragEnd = {
                        // 拖动结束
                    },
                    onDragCancel = {
                        // 拖动取消
                    },
                    onDrag = { _: PointerInputChange, dragAmount: Offset ->
                        // 拖动中
                        dragstartx += dragAmount.x
                        dragstarty += dragAmount.y
                        val x: Float = if (dragstarty < viewWeight / 2) {
                            dragAmount.x
                        } else {
                            -dragAmount.x
                        }
                        val y: Float = if (dragstartx < viewWeight / 2) {
                            -dragAmount.y
                        } else {
                            dragAmount.y
                        }//拆分坐标分量来进行顺逆的判断
                        dragOffest += x + y

                    }
                )
            }
    ) {
        translate(0f, 0f) {
            start = true//开始绘制动画
            var startAngle = dragOffest % 360//初始角度
            var selectAngle = 0f//记录被点击项的初始角度

            for ((i, p) in point.withIndex()) {
                val sweepAngle = p / sum * 360f//偏向的角度
                if (angles.size < point.size) {
                    angles.add(sweepAngle)
                }

                if (position != i) {

                    var angle = startAngle % 360 + sweepAngle * sweepPre * 0.5
                    angle = angle * Math.PI / 180// 要转弧度
                    val y = sin(angle) * 10
                    val x = cos(angle) * 10

                    drawArc(
                        color = color[i],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * sweepPre,//1f 弥补部分精度不足问题
                        useCenter = false, // 指示圆弧是否闭合边界中心的标志
                        // 样式
                        style = Stroke(width = drawHeight, miter = 0f, cap = StrokeCap.Butt),
                        size = Size(
                            (viewWeight - drawHeight * 2),
                            (viewWeight - drawHeight * 2)
                        ),
                        topLeft = Offset((drawHeight + x).toFloat(), (drawHeight + y).toFloat())
                    )

                    drawArc(
                        color = color[i],
                        alpha = 0.5f,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * sweepPre,
                        useCenter = false, // 指示圆弧是否闭合边界中心的标志
                        // 样式
                        style = Stroke(width = drawHeight / 5, miter = 8f, cap = StrokeCap.Butt),
                        size = Size(viewWeight - drawHeight * 3, viewWeight - drawHeight * 3),
                        topLeft = Offset((drawHeight * 1.5f + x).toFloat(), (drawHeight * 1.5f + y).toFloat())
                    )


                } else {
                    selectAngle = startAngle
                }
                startAngle += sweepAngle
            }

            //中间文本绘制
            if (position >= 0) {
                //选择的内容
                val sweepAngle = point[position] / sum * 360f

                var angle = selectAngle + (sweepAngle * sweepPre) * 0.5
                angle = angle * Math.PI / 180 // 要转弧度
                val y = sin(angle) * 10
                val x = cos(angle) * 10

                drawArc(
                    color = color[position],
                    startAngle = selectAngle,
                    sweepAngle = sweepAngle * sweepPre,
                    useCenter = false, // 指示圆弧是否闭合边界中心的标志
                    // 样式
                    style = Stroke(
                        width = drawHeight + selecyAddHeight,
                        miter = 8f,
                        cap = StrokeCap.Butt
                    ),
                    size = Size(
                        viewWeight - drawHeight * 2 + selecyAddHeight,
                        viewWeight - drawHeight * 2 + selecyAddHeight
                    ),
                    topLeft = Offset(
                        (drawHeight - selecyAddHeight / 2 + x).toFloat(),
                        (drawHeight - selecyAddHeight / 2 + y).toFloat()
                    )
                )//选择后宽度

                drawArc(
                    color = color[position],
                    alpha = 0.5f,
                    startAngle = selectAngle,
                    sweepAngle = sweepAngle * sweepPre,
                    useCenter = false, // 指示圆弧是否闭合边界中心的标志
                    style = Stroke(
                        width = drawHeight / 5,
                        miter = 8f,
                        cap = StrokeCap.Butt
                    ),                          // 样式
                    size = Size(viewWeight - drawHeight * 3, viewWeight - drawHeight * 3),
                    topLeft = Offset(
                        (drawHeight * 1.5f + x).toFloat(),
                        (drawHeight * 1.5f + y).toFloat()
                    )
                )

                //中间文本绘制
                val textWeight = paint.measureText(labels[position])
                val pointF = "%.1f".format(sweepAngle * 100 / 360)
                val text = "($pointF%)"
                val pointWeight = paint.measureText(text)
                //用原生Canvas来绘制
                drawContext.canvas.nativeCanvas.drawText(
                    labels[position],
                    viewWeight / 2 - textWeight / 2,
                    viewWeight / 2,
                    paint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    text,
                    viewWeight / 2 - pointWeight / 2,
                    viewWeight / 2 + paint.textSize,
                    paint
                )
            }
        }
    }
}