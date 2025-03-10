/*
 *
 *    Copyright 2016 jshook
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 * /
 */

package io.nosqlbench.engine.api.templating;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class StrInterpolatorTest {

    private static final List<Map<String, String>> abcd = new ArrayList<Map<String, String>>() {{
        add(
                new HashMap<String,String>() {{
                    put("akey", "aval1");
                    put("bkey", "bval1");
                    put("ckey", "cval1");
                }}
        );
        add(
                new HashMap<String,String>() {
                    {
                        put("akey", "aval2");
                        put("bkey", "bval2");
                    }
                }
        );
        add(
                new HashMap<String,String>() {
                    {
                        put("json-a-b", "'a': 'b'");
                    }
                }
        );
    }};

    private static final StrInterpolator interp = new StrInterpolator(abcd);

    @Test
    public void shouldReturnIdentity() {
        String a = interp.apply("A");
        assertThat(a).isEqualTo("A");
    }

    @Test
    public void shouldMatchSimpleSubst() {
        String a = interp.apply("<<akey>>");
        assertThat(a).isEqualTo("aval1");
    }

    @Test
    public void shouldMatchAlternateSubst() {
        String a = interp.apply("TEMPLATE(akey)");
        assertThat(a).isEqualTo("aval1");
        String b = interp.apply("TEMPLATE(nokeymatches,value2)");
        assertThat(b).isEqualTo("value2");
    }

    @Test
    public void shouldReturnWarningWhenUnmatched() {
        String a = interp.apply("<<nokeymatchesthis>>");
        assertThat(a).isEqualTo("UNSET:nokeymatchesthis");
    }

    @Test
    public void shouldReturnDefaultWhenNotOverridden() {
        String a = interp.apply("<<nokeymatchesthis:butithasadefault>>");
        assertThat(a).isEqualTo("butithasadefault");
    }

    @Test
    public void shouldOverrideDefault() {
        String a = interp.apply("<<bkey:bkeydefault>>");
        assertThat(a).isEqualTo("bval1");
    }

    @Test
    public void shouldWorkWithOddCharacters() {
        String a = interp.apply("<<unchanged:{'parm1':'val1',parm2:val2, parm3: 'val3'}>>");
        assertThat(a).isEqualTo("{'parm1':'val1',parm2:val2, parm3: 'val3'}");
    }

    @Test
    public void shouldWorkWithAllQuotes() {
        String a = interp.apply("<<Token:'Key': 'Value'>>");
        assertThat(a).isEqualTo("'Key': 'Value'");
    }

    @Test
    public void shouldWorkWithAllQuotesOverride() {
        String a = interp.apply("<<Token:'Key': 'Value'>>");
        assertThat(a).isEqualTo("'Key': 'Value'");
        String b = interp.apply("<<json-a-b:'Key': 'Value'>>");
        assertThat(b).isEqualTo("'a': 'b'");
    }

    @Test
    public void shouldWorkWithMultipleGroups() {
        String a = interp.apply("<<Token:'Key': 'Value'>>.<<Token2:'Stuff'>>");
        assertThat(a).isEqualTo("'Key': 'Value'.'Stuff'");
    }

//    @Test
//    public void shouldExpandNestedTemplates() {
//        String a = interp.apply("-TEMPLATE(akey,TEMPLATE(dkey,whee)-");
//        assertThat(a).isEqualTo("-aval1-");
//        String b = interp.apply("-TEMPLATE(unknown,TEMPLATE(bkey,whee))-");
//        assertThat(b).isEqualTo("-bval1-");
//    }
//
//    @Test
//    public void shouldGetBasicDetails() {
//        LinkedHashMap<String, String> details = interp.getTemplateDetails("-TEMPLATE(akey,TEMPLATE(dkey,whee)-");
//        assertThat(details).containsOnlyKeys("akey","dkey");
//        assertThat(details).containsValues("test1");
//
//    }
//
}
