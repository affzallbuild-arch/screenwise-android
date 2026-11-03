import com.screenwise.app.usage.*
import com.screenwise.app.util.WellnessScore
import java.time.*

fun main() {
    val s = UsageMath.sessions(listOf(EventPoint(100,"a",Action.OPEN),EventPoint(160,"b",Action.OPEN),EventPoint(200,"b",Action.CLOSE)),100,300)
    check(UsageMath.byApp(s)["a"] == 60L)
    check(UsageMath.byApp(s)["b"] == 40L)
    check(UsageMath.sessions(listOf(EventPoint(80,"a",Action.OPEN),EventPoint(140,"a",Action.CLOSE)),100,200).single().millis==40L)
    check(UsageMath.sessions(listOf(EventPoint(100,"a",Action.OPEN),EventPoint(130,null,Action.SCREEN_OFF)),100,500).single().millis==30L)
    check(UsageMath.sessions(emptyList(),100,200).isEmpty())
    check(WellnessScore.calculate(50,120,0,1,1,1,null).total == 90)
    println("PASS: 6 pure-Kotlin usage calculation / score tests")
}
