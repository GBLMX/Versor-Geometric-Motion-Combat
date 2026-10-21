#include <vsr/runtime.hpp>
#include <chrono>
#include <iostream>
#include <random>
#include <vector>
using namespace vsr::runtime;
using Clock = std::chrono::steady_clock;
static double ms(Clock::time_point a, Clock::time_point b){ return std::chrono::duration<double,std::milli>(b-a).count(); }
int main(){
  std::mt19937 rng(42);
  std::uniform_real_distribution<double> u(-1,1);
  volatile double sink=0;
  const int N=200000;
  auto a=E3::vec(u(rng),u(rng),u(rng));
  auto b=E3::vec(u(rng),u(rng),u(rng));
  auto t0=Clock::now();
  for(int i=0;i<N;++i){ auto c=a.gp(b); sink += c.get(i&7); }
  auto t1=Clock::now();
  std::cout<<"E3 gp          "<<N<<" it: "<<ms(t0,t1)<<" ms\n";

  const int B=1024, R=200;
  std::vector<Multivector> vs; vs.reserve(B);
  for(int i=0;i<B;++i) vs.push_back(E3::vec(u(rng),u(rng),u(rng)));
  auto batch=MultivectorBatch::of(vs);
  auto rotor=E3::rotor(0.3,-0.7,0.2,0.9);
  t0=Clock::now();
  for(int i=0;i<R;++i){ auto out=batch.sandwich(rotor); sink += out.get(0).get(1); }
  t1=Clock::now();
  std::cout<<"E3 batch fast  "<<R<<"x"<<B<<": "<<ms(t0,t1)<<" ms\n";
  t0=Clock::now();
  for(int i=0;i<R;++i){ auto out=batch.gp(rotor).gp_right(rotor.reverse()); sink += out.get(0).get(1); }
  t1=Clock::now();
  std::cout<<"E3 batch generic "<<R<<"x"<<B<<": "<<ms(t0,t1)<<" ms\n";

  std::vector<Multivector> ps; ps.reserve(B);
  for(int i=0;i<B;++i) ps.push_back(P3::point(u(rng),u(rng),u(rng)));
  auto pbatch=MultivectorBatch::of(ps);
  auto motor=P3::motor(P3::rotor(0.3,-0.7,0.2,0.9),P3::translator(1.5,-2.5,0.75));
  t0=Clock::now();
  for(int i=0;i<R;++i){ auto out=pbatch.sandwich(motor); sink += out.get(0).get(14); }
  t1=Clock::now();
  std::cout<<"PGA batch fast  "<<R<<"x"<<B<<": "<<ms(t0,t1)<<" ms\n";
  t0=Clock::now();
  for(int i=0;i<R;++i){ auto out=pbatch.gp(motor).gp_right(motor.reverse()); sink += out.get(0).get(14); }
  t1=Clock::now();
  std::cout<<"PGA batch generic "<<R<<"x"<<B<<": "<<ms(t0,t1)<<" ms\n";

  Signature high=Signature::euclidean(12);
  auto sa=SparseMultivector::of(high,{1,2,4,8,16,32,64,128,256,512},{0.5,-0.3,0.7,0.1,-0.2,0.4,0.9,-0.6,0.2,0.8});
  auto sb=SparseMultivector::of(high,{1,2,4,8,16,32,64,128,256,512},{0.1,0.2,-0.3,0.4,0.5,-0.6,0.7,-0.8,0.9,0.1});
  const int S=100000;
  t0=Clock::now();
  for(int i=0;i<S;++i){ auto c=sa.gp(sb); sink += c.max_abs(); }
  t1=Clock::now();
  std::cout<<"Cl(12) sparse gp "<<S<<": "<<ms(t0,t1)<<" ms\n";

  const int M=8, MR=2000;
  MultivectorMatrix m(P3::SIG,M,M), n(P3::SIG,M,M);
  for(int r=0;r<M;++r) for(int c=0;c<M;++c){
    m.set(r,c,P3::motor(P3::rotor(u(rng),u(rng),u(rng),u(rng)*2),P3::translator(u(rng),u(rng),u(rng))));
    n.set(r,c,P3::motor(P3::rotor(u(rng),u(rng),u(rng),u(rng)*2),P3::translator(u(rng),u(rng),u(rng))));
  }
  t0=Clock::now();
  for(int i=0;i<MR;++i){ auto p=m.multiply(n); sink += p.get(0,0).max_abs(); }
  t1=Clock::now();
  std::cout<<"matrix 8x8 multiply "<<MR<<": "<<ms(t0,t1)<<" ms\n";

  t0=Clock::now();
  for(int i=0;i<100;++i){ auto inv=m.inverse(); sink += inv.get(0,0).max_abs(); }
  t1=Clock::now();
  std::cout<<"matrix 8x8 inverse 100: "<<ms(t0,t1)<<" ms\n";
  std::cout<<"sink "<<sink<<"\n";
}
