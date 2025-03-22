CXX = g++
CXXFLAGS = -std=c++11 -Wall -O2

all: fpmac fpmac_detailed

fpmac: fpmac.cpp
	$(CXX) $(CXXFLAGS) -o fpmac fpmac.cpp

fpmac_detailed: fpmac_detailed.cpp
	$(CXX) $(CXXFLAGS) -o fpmac_detailed fpmac_detailed.cpp

run: fpmac
	./fpmac

run_detailed: fpmac_detailed
	./fpmac_detailed

clean:
	rm -f fpmac fpmac_detailed 