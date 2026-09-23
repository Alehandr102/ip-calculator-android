package com.example.ipcalculator

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertThrows

class IpCalculatorTest {

    @Test
    fun calculate24ReturnsExpectedSubnet(){
        val result = IpCalculator.calculate("192.168.1.10", 24)

        assertEquals("192.168.1.0", result.networkAddress)
        assertEquals(24,result.cidr)
        assertEquals("255.255.255.0", result.subnetMask)
        assertEquals("0.0.0.255",result.wildcardMask)
        assertEquals("192.168.1.255", result.broadcastAddress)
        assertEquals("192.168.1.1", result.firstUsableIp)
        assertEquals("192.168.1.254", result.lastUsableIp)
        assertEquals(256L,result.totalAddresses)
        assertEquals(254L,result.usableHosts)
    }

    @Test
    fun calculate8ReturnsExpectedSubnet(){
        val result = IpCalculator.calculate("10.0.0.1", "255.0.0.0")

        assertEquals("10.0.0.0", result.networkAddress)
        assertEquals(8,result.cidr)
        assertEquals("255.0.0.0", result.subnetMask)
        assertEquals("0.255.255.255",result.wildcardMask)
        assertEquals("10.255.255.255", result.broadcastAddress)
        assertEquals("10.0.0.1", result.firstUsableIp)
        assertEquals("10.255.255.254", result.lastUsableIp)
        assertEquals(16777216L,result.totalAddresses)
        assertEquals(16777214L,result.usableHosts)

    }

    @Test
    fun calculate32ReturnsSingleAddress() {
        val result = IpCalculator.calculate("192.168.1.77", 32)

        assertEquals("192.168.1.77", result.networkAddress)
        assertEquals(32, result.cidr)
        assertEquals("192.168.1.77", result.firstUsableIp)
        assertEquals("192.168.1.77", result.lastUsableIp)
        assertEquals(1L, result.totalAddresses)
        assertEquals(1L, result.usableHosts)
    }

    @Test
    fun calculate31ReturnsTwoHosts() {
        val result = IpCalculator.calculate("192.168.1.76", 31)

        assertEquals("192.168.1.76", result.firstUsableIp)
        assertEquals("192.168.1.77", result.lastUsableIp)
        assertEquals(2L, result.usableHosts)
    }

    @Test
    fun invalidInputThrows(){
        assertThrows(IllegalArgumentException::class.java){
            IpCalculator.calculate("192.168.1.256", 24)
        }

        assertThrows(IllegalArgumentException::class.java){
            IpCalculator.calculate("192.168.1.10", 33)
        }

        assertThrows(IllegalArgumentException::class.java){
            IpCalculator.calculate("192.168.1.10", "255.0.255.0")
        }
    }
}