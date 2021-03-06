<?php

    preg_match(<error descr="[EA] [\d\w] is 'greedy'. Please remove \d as it's a subset of \w.">'/[\d\w]/'</error>,     '');
    preg_match(<error descr="[EA] [\d\s\w] is 'greedy'. Please remove \d as it's a subset of \w.">'/[\d\s\w]/'</error>, '');
    preg_match('/[\d\s\[\]]/', '');

    preg_match(<error descr="[EA] [\D\W] is 'greedy'. Please remove \D as it's a subset of \W.">'/[\D\W]/'</error>,     '');
    preg_match(<error descr="[EA] [\D\W\S] is 'greedy'. Please remove \D as it's a subset of \W.">'/[\D\W\S]/'</error>, '');
    preg_match('/[\D\W\[\]]/', '');