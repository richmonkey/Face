#ifndef IM_UTIL_H
#define IM_UTIL_H
#include <stdint.h>

void writeInt32(int32_t v, void *p);
int32_t readInt32(const void *p);

void writeInt64(int64_t v, void *p);
int64_t readInt64(const void *p);
#endif
