#include <vsr/space/vsr_ega3D_types.h>
#include <vsr/space/vsr_pga3D_types.h>
#ifdef WITH_RUNTIME
#include <vsr/runtime.hpp>
#endif
#include <algorithm>
#include <array>
#include <chrono>
#include <cstdio>
#include <random>
#include <vector>

using Clock = std::chrono::steady_clock;
static double ms(Clock::time_point a, Clock::time_point b) {
    return std::chrono::duration<double, std::milli>(b - a).count();
}

template <class F>
static double best_of(F&& f, int reps) {
    double best = 1e300;
    for (int r = 0; r < 3; ++r) {
        volatile double sink = 0;
        auto t0 = Clock::now();
        for (int i = 0; i < reps; ++i) {
            double v = f(i);
            sink = sink + v;
        }
        auto t1 = Clock::now();
        (void)sink;
        best = std::min(best, ms(t0, t1));
    }
    return best;
}

int main() {
    std::mt19937 rng(42);
    std::uniform_real_distribution<double> u(-1, 1);
    constexpr int K = 16;

    std::vector<vsr::ega::Vec> e3a, e3b;
    e3a.reserve(K); e3b.reserve(K);
    for (int i = 0; i < K; ++i) {
        e3a.emplace_back(u(rng), u(rng), u(rng));
        e3b.emplace_back(u(rng), u(rng), u(rng));
    }
    std::vector<vsr::pga::Pnt> pp;
    std::vector<vsr::pga::Mot> mm;
    pp.reserve(K); mm.reserve(K);
    for (int i = 0; i < K; ++i) {
        pp.emplace_back(u(rng), u(rng), u(rng), u(rng));
        mm.emplace_back(u(rng), u(rng), u(rng), u(rng), u(rng), u(rng), u(rng), u(rng));
    }

    const int N = 1000000;
    double ct_e3 = best_of([&](int i) {
        auto c = e3a[i & 15] * e3b[(i * 7) & 15];
        double acc = 0;
        for (int j = 0; j < c.Num; ++j) acc += c[j];
        return acc;
    }, N);
    double ct_pga = best_of([&](int i) {
        auto q = pp[i & 15].spin(mm[(i * 3) & 15]);
        double acc = 0;
        for (int j = 0; j < q.Num; ++j) acc += q[j];
        return acc;
    }, N);
    std::printf("compile E3 gp       %8.3f ms  (%6.2f ns/op)\n", ct_e3, ct_e3 * 1e6 / N);
    std::printf("compile PGA spin    %8.3f ms  (%6.2f ns/op)\n", ct_pga, ct_pga * 1e6 / N);

    // Compile-time scalar batch baselines (same 200x1024 work as runtime batches).
    constexpr int B = 1024, R = 200;
    std::vector<vsr::ega::Vec> eb;
    eb.reserve(B);
    for (int i = 0; i < B; ++i) eb.emplace_back(u(rng), u(rng), u(rng));
    vsr::ega::Rot er(u(rng), u(rng), u(rng), u(rng));
    double ct_e3_batch = best_of([&](int) {
        double acc = 0;
        for (auto& v : eb) {
            auto out = v.spin(er);
            for (int j = 0; j < out.Num; ++j) acc += out[j];
        }
        return acc;
    }, R);
    std::vector<vsr::pga::Pnt> pb;
    pb.reserve(B);
    for (int i = 0; i < B; ++i) pb.emplace_back(u(rng), u(rng), u(rng), u(rng));
    vsr::pga::Mot pm(u(rng), u(rng), u(rng), u(rng), u(rng), u(rng), u(rng), u(rng));
    double ct_pga_batch = best_of([&](int) {
        double acc = 0;
        for (auto& p : pb) {
            auto out = p.spin(pm);
            for (int j = 0; j < out.Num; ++j) acc += out[j];
        }
        return acc;
    }, R);
    std::printf("compile E3 batch    %8.3f ms  (%6.2f ns/op)\n", ct_e3_batch,
                ct_e3_batch * 1e6 / (B * R));
    std::printf("compile PGA batch   %8.3f ms  (%6.2f ns/op)\n", ct_pga_batch,
                ct_pga_batch * 1e6 / (B * R));

#ifdef WITH_RUNTIME
    using vsr::runtime::Multivector;
    using vsr::runtime::MultivectorBatch;
    using vsr::runtime::E3;
    using vsr::runtime::P3;

    std::vector<Multivector> re3a, re3b;
    re3a.reserve(K); re3b.reserve(K);
    for (int i = 0; i < K; ++i) {
        re3a.push_back(E3::vec(u(rng), u(rng), u(rng)));
        re3b.push_back(E3::vec(u(rng), u(rng), u(rng)));
    }
    std::vector<Multivector> rpp, rmm;
    rpp.reserve(K); rmm.reserve(K);
    for (int i = 0; i < K; ++i) {
        rpp.push_back(P3::point(u(rng), u(rng), u(rng)));
        rmm.push_back(P3::motor(P3::rotor(u(rng), u(rng), u(rng), u(rng)),
                                P3::translator(u(rng), u(rng), u(rng))));
    }

    double rt_e3 = best_of([&](int i) {
        auto c = re3a[i & 15].gp(re3b[(i * 7) & 15]);
        double acc = 0;
        for (int j = 0; j < c.size(); ++j) acc += c.get(j);
        return acc;
    }, N);
    double rt_pga = best_of([&](int i) {
        auto q = rmm[(i * 3) & 15].sandwich(rpp[i & 15]);
        double acc = 0;
        for (int m : {7, 11, 13, 14}) acc += q.get(m);
        return acc;
    }, N);
    std::printf("runtime E3 gp       %8.3f ms  (%6.2f ns/op)\n", rt_e3, rt_e3 * 1e6 / N);
    std::printf("runtime PGA spin    %8.3f ms  (%6.2f ns/op)\n", rt_pga, rt_pga * 1e6 / N);

    std::vector<Multivector> rvec;
    rvec.reserve(B);
    for (int i = 0; i < B; ++i) rvec.push_back(E3::vec(u(rng), u(rng), u(rng)));
    auto erb = MultivectorBatch::of(rvec);
    auto rotor = E3::rotor(u(rng), u(rng), u(rng), u(rng));
    auto consume_e3_batch = [](const MultivectorBatch& out) {
        double acc = 0;
        for (int j = 0; j < out.size(); ++j)
            for (int m : {1, 2, 4}) acc += out.get(m, j);
        return acc;
    };
    double rt_e3_fast = best_of([&](int) {
        return consume_e3_batch(erb.sandwich(rotor));
    }, R);
    double rt_e3_generic = best_of([&](int) {
        return consume_e3_batch(erb.gp(rotor).gp_right(rotor.reverse()));
    }, R);
    std::printf("runtime E3 batch fast    %8.3f ms  (%6.2f ns/op)\n", rt_e3_fast,
                rt_e3_fast * 1e6 / (B * R));
    std::printf("runtime E3 batch generic %8.3f ms  (%6.2f ns/op)\n", rt_e3_generic,
                rt_e3_generic * 1e6 / (B * R));

    std::vector<Multivector> rp;
    rp.reserve(B);
    for (int i = 0; i < B; ++i) rp.push_back(P3::point(u(rng), u(rng), u(rng)));
    auto rpb = MultivectorBatch::of(rp);
    auto motor = P3::motor(P3::rotor(u(rng), u(rng), u(rng), u(rng)),
                           P3::translator(u(rng), u(rng), u(rng)));
    auto consume_pga_batch = [](const MultivectorBatch& out) {
        double acc = 0;
        for (int j = 0; j < out.size(); ++j)
            for (int m : {7, 11, 13, 14}) acc += out.get(m, j);
        return acc;
    };
    double rt_pga_fast = best_of([&](int) {
        return consume_pga_batch(rpb.sandwich(motor));
    }, R);
    double rt_pga_generic = best_of([&](int) {
        return consume_pga_batch(rpb.gp(motor).gp_right(motor.reverse()));
    }, R);
    std::printf("runtime PGA batch fast    %8.3f ms  (%6.2f ns/op)\n", rt_pga_fast,
                rt_pga_fast * 1e6 / (B * R));
    std::printf("runtime PGA batch generic %8.3f ms  (%6.2f ns/op)\n", rt_pga_generic,
                rt_pga_generic * 1e6 / (B * R));
#endif
}
