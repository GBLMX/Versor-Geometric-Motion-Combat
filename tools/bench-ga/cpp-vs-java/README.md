# C++ versor vs versor-java benchmark archive

归档日期：2026-10-21。对同一组几何代数工作负载，比较：

- C++ 上游编译期模板：`wolftype/versor` `8262fce`
- C++ fork 编译期 + runtime：`GBLMX/versor` `bfba1f2`
- Java dense/runtime：`versor-java` `3b1515b`

## 工作负载

| 名称 | C++ | Java |
|---|---|---|
| E3 gp | `ega::Vec * ega::Vec` / runtime `E3::vec().gp()` | `E3.vec().gp()` |
| PGA spin | `pga::Pnt::spin(pga::Mot)` / runtime `Mot::sandwich(Pnt)` | `P3.motor(...).sandwich(P3.point(...))` |
| E3 batch | 1024 个 E3 向量做 rotor sandwich，200 次取每点均值 | `MultivectorBatch.sandwich` |
| PGA batch | 1024 个 PGA 点做 motor sandwich，200 次取每点均值 | `MultivectorBatch.sandwich` |

C++ 和 Java 端都消费结果的全部几何分量，避免死代码消除；输入在多个对象间轮换，避免循环外提。

## 复现

### C++

`perf_compare.cpp` 使用上游编译期 API 时无需额外宏；编译到 fork 并启用 runtime 时加 `-DWITH_RUNTIME`。

```bash
CXX=${CXX:-g++}
CPP_INCLUDE=/path/to/versor/include
CPP_FORK_INCLUDE=/path/to/versor-fork/include

$CXX -std=c++26 -O3 -DNDEBUG -I "$CPP_INCLUDE" \
    perf_compare.cpp -o /tmp/perf_upstream
$CXX -std=c++26 -O3 -DNDEBUG -I "$CPP_FORK_INCLUDE" -DWITH_RUNTIME \
    perf_compare.cpp -o /tmp/perf_fork

/tmp/perf_upstream
/tmp/perf_fork
```

### Java JMH

需要 JDK 25（或用与归档结果相同的 JVM）和 `versor-java` 自带的 JMH jar：

```bash
ROOT=/path/to/versor-java
JAVA_HOME=/path/to/jdk-25
JMH_LIB="$ROOT/lib/versor-java/bench-jmh/lib"
CP="$JMH_LIB/jmh-core-1.37.jar:$JMH_LIB/jopt-simple-5.0.4.jar:$JMH_LIB/commons-math3-3.6.1.jar"
AP="$JMH_LIB/jmh-generator-annprocess-1.37.jar"

"$JAVA_HOME/bin/javac" --release 17 -cp "$CP" -processorpath "$CP:$AP" \
    -d /tmp/cpp-vs-java-out \
    $(find "$ROOT/lib/versor-java/src/main/java" -name '*.java') CppCompareJmh.java

"$JAVA_HOME/bin/java" -Xmx4g -cp "/tmp/cpp-vs-java-out:$CP" \
    org.openjdk.jmh.Main 'vsr\.CppCompareJmh' \
    -f 1 -wi 5 -i 5 -w 1s -r 1s -t 1
```

## 归档结果

环境：GCC 16.2.1，`-O3 -DNDEBUG -std=c++26`，3 次取最好；Java JDK 25.0.3 (JBR)，JMH 1.37，5×1s 预热 + 5×1s 测量，单线程。

| 运算 | C++ 上游编译期 | C++ fork runtime | Java JMH | Java / C++ runtime | Java / C++ 编译期 |
|---|---:|---:|---:|---:|---:|
| E3 gp | 1.92 ns | 46.50 ns | 46.74 ± 1.88 ns | 1.01× | 24.3× |
| PGA spin | 8.96 ns | 29.98 ns | 36.01 ± 2.18 ns | 1.20× | 4.02× |
| E3 batch | 3.96 ns* | 7.43 ns | 8.53 ± 0.70 ns | 1.15× | 2.15× |
| PGA batch | 7.89 ns* | 16.83 ns | 19.21 ± 1.14 ns | 1.14× | 2.43× |

`*` 上游没有 batch API，使用标量循环基线；Java 和 C++ runtime 都是 SoA 批量路径。

原始数据见 `results.csv`。

## 注意

- 这是微基准，不是应用端到端测量；不同机器的绝对值不可直接比较。
- Java JMH 与 C++ 手写定时循环的测量方法不同，应优先看同一实现内部的相对关系；小于约 10–15% 的差异不应过度解读。
- C++ 编译期模板与 C++/Java runtime 属于不同的实现模型：前者在编译期展开为固定公式，后者保留运行期签名和数组分配。
